package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.mapper.StorageCleanupTaskMapper;
import com.easypan.model.entity.StorageCleanupTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 存储文件清理任务服务
 * 用于管理【孤儿文件异步清理任务】
 * 场景：文件上传事务回滚、程序异常宕机产生磁盘孤儿文件，通过任务表异步重试删除物理文件
 * 设计要点：
 * 1. 所有写操作使用 REQUIRES_NEW 新建独立事务，不依附外部主事务
 * 2. 支持失败重试、延迟执行、批量拉取待清理任务
 * 3. 存储路径不与drive_file建立外键，兼容孤儿文件无元数据的场景
 */
@Service
@RequiredArgsConstructor
public class StorageCleanupTaskService {
    private static final String PENDING = "PENDING";
    private final StorageCleanupTaskMapper taskMapper;

    /**
     * 入队清理任务（新增或更新待清理任务）
     * 使用REQUIRES_NEW开启全新独立事务
     * 核心意义：外部文件上传事务回滚不会影响本条任务入库，保证孤儿文件可以被后续定时任务扫描清理
     *
     * @param storagePath 待删除磁盘物理文件完整路径
     * @param reason      文件需要清理的原因（例如：事务回滚、上传失败）
     * @param lastError   最近一次删除失败异常信息
     * @param nextRetryAt 下次重试时间；null代表立即执行
     */
    //开启一个全新独立事务；如果外层已经存在事务，外层事务会先暂停，新事务完全不受外层事务影响。
    //新事务提交 / 回滚，完全和外层互不干扰！
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(
            String storagePath,
            String reason,
            String lastError,
            LocalDateTime nextRetryAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.upsertPending(
                storagePath,
                abbreviate(reason, 100),
                //字符串截断工具方法
                //含义：把错误消息最多保留 1000 个字符，超长部分直接截断
                abbreviate(lastError, 1000),
                nextRetryAt == null ? now : nextRetryAt,
                now
        );
    }

    /**
     * 查询当前到达执行时间、待处理的清理任务（批量拉取）
     * 由定时任务调度器调用，获取可以执行的孤儿文件删除任务
     *
     * @param now       当前系统时间
     * @param batchSize 单次拉取最大任务数量，控制IO压力
     * @return 待执行清理任务列表
     */
//    给数据库驱动提示：这条事务只查询，不会写数据
    @Transactional(readOnly = true)
    public List<StorageCleanupTask> findDueTasks(LocalDateTime now, int batchSize) {
        // 最小批量1，防止传入0/负数导致SQL异常
        int safeBatchSize = Math.max(1, batchSize);
        return taskMapper.selectList(
                new LambdaQueryWrapper<StorageCleanupTask>()
                        .eq(StorageCleanupTask::getStatus, PENDING)
                        // 条件2：下次执行时间 ≤ 当前时间
                        .le(StorageCleanupTask::getNextRetryAt, now)
                        //优先执行更早等待的任务：id兜底保证排序稳定
                        .orderByAsc(StorageCleanupTask::getNextRetryAt)
                        // 兜底排序：时间相同，按id从小到大，保证排序结果稳定不变
                        .orderByAsc(StorageCleanupTask::getId)
                        //只一次性取出最多 safeBatchSize 条清理任务批量处理
                        .last("LIMIT" + safeBatchSize)
        );
    }

    /**
     * 批量查询：传入一批文件路径，筛选出【已经存在清理任务】的路径集合
     * 作用：避免同一个文件重复加入清理队列，减少重复任务
     *
     * @param storagePaths 待检查的物理文件路径集合
     * @return 已经存在pending状态清理任务的路径集合
     */
    //上面的findDueTasks查询【待执行任务】，准备去删除文件	而这个是路径查重，判断【要不要新建清理任务】
    @Transactional(readOnly = true)
    public Set<String> findTrackedPaths(Collection<String> storagePaths) {
        if (storagePaths == null || storagePaths.isEmpty())
            // 空集合直接返回，避免无效SQL查询
            return Set.of();
        return taskMapper.selectList(
                        new LambdaQueryWrapper<StorageCleanupTask>()
                                .select(StorageCleanupTask::getStoragePath)
                                .eq(StorageCleanupTask::getStatus, PENDING)
                                .in(StorageCleanupTask::getStoragePath, storagePaths)
                )
                .stream()
                .map(StorageCleanupTask::getStoragePath)
                .collect(Collectors.toSet());
    }

    /**
     * 删除清理任务
     * 使用独立事务；文件成功删除后调用，销毁任务记录
     *
     * @param taskId 清理任务主键ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remove(Long taskId) {
        taskMapper.deleteById(taskId);
    }

    /**
     * 标记任务执行失败，更新重试次数、下次重试时间、异常日志
     * 物理文件删除失败（文件被占用、权限不足）时调用，等待延迟重试
     *
     * @param taskId      任务主键
     * @param attempts    当前累计尝试次数
     * @param nextRetryAt 计划下一次重试时间
     * @param lastError   本次失败异常信息
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            Long taskId,
            int attempts,
            LocalDateTime nextRetryAt,
            String lastError
    ) {
        StorageCleanupTask update = new StorageCleanupTask();
        update.setId(taskId);
        update.setStatus(PENDING);
        update.setAttempts(attempts);
        update.setNextRetryAt(nextRetryAt);
        update.setLastError(abbreviate(lastError, 1000));
        update.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }
    /**
     * 字符串截断工具方法
     * 数据库字段存在长度限制，超长文本进行截断，防止SQL报错
     *
     * @param value 原始字符串
     * @param maxLength 允许最大长度
     * @return 截断后字符串；null/空白原样返回
     */
    private String abbreviate(String value,int maxLength){
        if(value==null||value.isBlank())
            return null;
        return value.length()<=maxLength?value:value.substring(0,maxLength);
    }

}
