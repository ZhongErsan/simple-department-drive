package com.easypan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/*
 * 事务文件清理注册器
 * 作用：将【物理文件清理逻辑】绑定到当前正在执行的数据库事务生命周期
 * 业务场景：文件上传时，磁盘先写入文件，再开启事务写入DriveFile数据库记录
 *  1. 如果事务回滚 → 磁盘文件变成孤儿文件，自动登记清理任务
 *  2. 如果事务状态未知（网络异常、进程崩溃等）→ 延迟登记，等待安全窗口期后校验
 *  3. 如果事务正常提交 → 保留磁盘文件，不执行任何清理
 *
 * 核心机制：Spring事务同步器 TransactionSynchronization
 * 仅在存在活跃事务上下文时注册回调；无事务时注册失败，由上层自行处理
 */
@Component
@RequiredArgsConstructor
public class TransactionFileCleanupRegistrar {
    private final OrphanFileCleanupService cleanupService;
    /**
     * 注册事务完成回调：事务回滚后清理上传产生的物理文件
     * 调用时机：文件已经写入磁盘，准备执行DriveFile数据库插入逻辑之前
     *
     * @param storagePath 已经落地磁盘的上传文件相对路径
     * @return true 回调注册成功（当前存在活跃事务）；false 当前没有开启事务，注册失败
     */
    public boolean registerRollbackCleanup(String storagePath){
        if(!TransactionSynchronizationManager.isSynchronizationActive())
            return false;
        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                               //场景一：事务明确回滚，文件是孤儿，立刻尝试清理
                                if(status==STATUS_ROLLED_BACK)
                                    cleanupService.cleanupNowOrEnqueue(storagePath,"UPLOAD_TRANSACTION_ROLLBACK");
                                //场景二：事务状态不确定，不能立刻删除，需要延迟到安全窗口之后再校验数据库是否存在文件元数据
                                else if(status==STATUS_UNKNOWN)
                                    cleanupService.deferUntilReferenceCheck(storagePath,"UPLOAD_TRANSACTION_UNKNOWN");
                                TransactionSynchronization.super.afterCompletion(status);
                            }
                        }
                );
        //注册成功
        return true;
    }
    //事务提交成功之后才执行的文件清理
    public boolean registerAfterCommitCleanup(String storagePath,String reason){
        if(!TransactionSynchronizationManager.isSynchronizationActive())
            return false;
        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                cleanupService.cleanupNowOrEnqueue(storagePath,reason);
                            }
                        }
                );
        return true;
    }

}
