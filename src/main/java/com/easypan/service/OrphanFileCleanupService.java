package com.easypan.service;

import com.easypan.mapper.DriveFileMapper;
import com.easypan.mapper.StorageCleanupTaskMapper;
import com.easypan.model.entity.StorageCleanupTask;
import com.easypan.storage.LocalStorageService;
import com.easypan.storage.StorageCleanupProperties;
import com.easypan.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanFileCleanupService {
    private final LocalStorageService storageService;
    private final DriveFileMapper fileMapper;
    private final StorageCleanupProperties properties;
    private final StorageCleanupTaskService taskService;

    /**
     * 尝试立刻物理删除文件；删除失败则写入持久化清理任务等待后续重试
     * 使用场景：确认文件业务上不再需要（如用户主动删除文件、回收站永久删除）
     *
     * @param storagePath 待删除物理文件相对存储路径
     * @param reason      文件清理原因，用于日志追踪
     */
    public void cleanupNowOrEnqueue(String storagePath, String reason) {
        try {
            storageService.delete(storagePath);
            log.info("已清理物理文件，path={},reason={}", storagePath, reason);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "物理文件清理失败，准备写入重试任务，path={},reason={}",
                    storagePath,
                    reason,
                    cleanupFailure
            );
            try {
                // 写入持久化清理任务，立即进入待重试队列
                taskService.enqueue(
                        storagePath,
                        reason,
                        cleanupFailure.getMessage(),
                        LocalDateTime.now()
                );
            } catch (RuntimeException enqueueFailure) {
                // 使用addSuppressed保留两层异常，日志不会丢失原始失败信息
                cleanupFailure.addSuppressed(enqueueFailure);
                log.error(
                        "清理失败且无法写入重试任务，path={}",
                        storagePath,
                        cleanupFailure
                );
            }
        }
    }

    /**
     * 延迟登记清理任务
     * 使用场景：事务执行结果未知（上传事务回滚风险），不能立刻删除文件
     * 逻辑：先登记任务，但设置延迟执行；等待安全窗口期过后，再核对数据库引用状态
     * 避免误删处于事务提交阶段的正常新文件
     *
     * @param storagePath 文件存储路径
     * @param reason      登记原因
     */
    public void deferUntilReferenceCheck(
            String storagePath,
            String reason
    ) {
        try {
            taskService.enqueue(
                    storagePath,
                    reason,
                    "事务完成状态未知，等待核对数据库引用",
                    // 当前时间 + 安全窗口期，窗口期结束后才允许执行校验清理
                    LocalDateTime.now().plus(properties.orphanMinAge())
            );
        } catch (RuntimeException enqueueFailure) {
            log.error(
                    "无法等级事务状态未知的文件，path={}",
                    storagePath,
                    enqueueFailure
            );
        }
    }

    /**
     * 定时任务入口
     * fixedDelayString：上一轮任务**执行完毕后**，间隔指定毫秒再执行下一轮，防止任务堆积
     * initialDelayString：服务启动后延迟一段时间再首次执行，避免启动瞬间抢占IO资源
     * <p>
     * 一轮执行顺序：优先重试历史失败任务 → 再全盘扫描识别新孤儿文件
     */
    @Scheduled(
            initialDelayString =
                    "${storage.cleanup.initial-delay-ms:60000}",
            fixedDelayString =
                    "${storage.cleanup.scan-delay-ms:300000}"
    )
    public void runCleanupCycle() {
        //如果配置关闭清理功能，直接跳过本轮
        // 配置关闭清理功能，直接跳过本轮
        if (!properties.enabled()) {
            return;
        }

        try {
            retryPersistedTasks();
        } catch (RuntimeException e) {
            log.error("执行持久化清理任务失败", e);
        }

        try {
            scanAndCleanOrphans();
        } catch (RuntimeException e) {
            log.error("扫描孤儿文件失败", e);
        }
    }
    /**
     *批量重试数据库中持久化的待清理任务
     * 核心安全规则：**每次删除前必须重新查询数据库**
     * 场景：任务登记之后，如果用户重新上传同名文件，drive_file会新增记录，此时禁止删除
     */
    void retryPersistedTasks(){
        List<StorageCleanupTask> tasks=
                taskService.findDueTasks(
                        LocalDateTime.now(),
                        properties.batchSize()
                );
        for(StorageCleanupTask task:tasks){
            try{
                //再次校验数据库是否还存在该文件的有效元数据
                if(isReferenced(task.getStoragePath())){
                    taskService.remove(task.getId());
                    log.info(
                            "文件仍被数据库引用，取消清理任务，taskId={},path={}",
                            task.getAttempts(),
                            task.getStoragePath()
                    );
                    continue;
                }
                //确认无业务引用，执行物理删除
                storageService.delete(task.getStoragePath());
                taskService.remove(task.getId());
                log.info(
                        "孤儿文件重试清理成功，taskId={},path={}",
                        task.getId(),
                        task.getStoragePath()
                );
            }catch (RuntimeException cleanupFailure){
                int attempts=safeAttempts(task)+1;
                LocalDateTime nextRetryAt=
                        LocalDateTime.now()
                                .plus(calculateRetryDelay(attempts));
                try{
                    taskService.markFailed(
                            task.getId(),
                            attempts,
                            nextRetryAt,
                            cleanupFailure.getMessage()
                    );
                }catch (RuntimeException updateFailure){
                    cleanupFailure.addSuppressed(updateFailure);
                    log.error(
                            "更新清理任务失败，taskId={},path={}",
                            task.getId(),
                            task.getStoragePath(),
                            cleanupFailure
                    );
                    continue;
                }
                log.warn(
                        "孤儿文件清理失败，taskId={},attempts={},nextRetryAt={}",
                        task.getId(),
                        attempts,
                        nextRetryAt,
                        cleanupFailure
                );
            }
        }
    }
    /**
     * 查询数据库，判断该文件路径是否存在有效drive_file记录
     * @param storagePath 文件存储路径
     * @return true=存在业务引用（有效文件）；false=无引用（孤儿文件）
     */
    private boolean isReferenced(String storagePath){
        return !fileMapper.selectExistingStoragePaths(List.of(storagePath)).isEmpty();
    }
    /**
     * 全盘扫描磁盘，识别孤儿文件（兜底方案）
     * 适用场景：服务异常崩溃、回调没有机会执行，没有生成清理任务的孤儿文件
     * 流程：
     * 1. 筛选超过安全窗口期的老旧文件
     * 2. 批量查询数据库，筛选存在元数据的正常文件
     * 3. 排除已经存在清理任务的文件，避免重复入队
     * 4. 符合条件则创建清理任务
     */
    void scanAndCleanOrphans(){
        Instant cutoff=Instant.now().minus(properties.orphanMinAge());
        List<StorageObject> candidates=storageService.listFilesOlderThan(cutoff,properties.batchSize());
        if(candidates.isEmpty())
            return;
        // 提取所有待校验文件路径
        List<String> paths=candidates.stream()
                .map(StorageObject::storagePath)
                .toList();
        //批量查询数据库内存在有效元数据的文件路径
        Set<String> referencePaths=new HashSet<>(fileMapper.selectExistingStoragePaths(paths));
        //批量查询已经登记清理任务的文件
        Set<String> trackedPaths=taskService.findTrackedPaths(paths);
        for(StorageObject candidate: candidates){
            String storagePath=candidate.storagePath();
            boolean referenced=referencePaths.contains(storagePath);
            boolean alreadyTracked=trackedPaths.contains(storagePath);
            //数据库没有引用并且尚未创建清理任务，就被判定为故而文件，等级清理
            if(!referenced&&!alreadyTracked){
                cleanupNowOrEnqueue(storagePath,"ORPHAN_SCAN");
            }
        }
    }
    /**
     * 安全获取当前任务重试次数，处理数据库字段为null的情况
     * @param task 清理任务
     * @return 重试次数，最小返回0
     */
    private int safeAttempts(StorageCleanupTask task){
        if(task.getAttempts()==null)
            return 0;
        return Math.max(0,task.getAttempts());
    }
    /**
     * 指数退避重试间隔计算
     * 策略示例（基础间隔1分钟，上限1小时）：
     * 第1次失败 → 1min
     * 第2次失败 → 2min
     * 第3次失败 → 4min
     * 持续倍增，到达maxDelay后不再拉长间隔
     *
     * @param attempts 当前累计重试次数
     * @return 本次下一次等待时长
     */
    private Duration calculateRetryDelay(int attempts){
        Duration delay=properties.retryBaseDelay();
        //最多连续倍增20次
        int doublings=Math.min(Math.max(attempts-1,0),20);
        for(int i=0;i<doublings;i++){
            if(delay.compareTo(properties.retryMaxDelay())>=0)
                return properties.retryMaxDelay();
            delay=delay.multipliedBy(2);
        }
        if(delay.compareTo(properties.retryMaxDelay())>0)
            return properties.retryMaxDelay();
        return delay;
    }

}
