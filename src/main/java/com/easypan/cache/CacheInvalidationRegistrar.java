package com.easypan.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 缓存失效注册器
 * 解决「数据库事务还没提交，缓存就被删掉」导致的并发脏读问题
 * 核心功能：**如果当前方法处在数据库事务中，等事务提交成功之后，再删除Redis缓存；无事务则直接删缓存**
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationRegistrar {
    // 注入前面封装好的Redis缓存操作服务
    private final RedisDataCacheService cacheService;

    /**
     * 删除缓存入口方法
     * @param keys 要删除的Redis key，支持多个key批量删除
     */
    public void evictAfterCommit(
            String... keys
    ) {
        /*
         判断当前线程是否处于激活的数据库事务中
         isSynchronizationActive() = true：代码在 @Transactional 事务方法内部执行
         isSynchronizationActive() = false：没有事务
         */
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            // 没有事务：直接删除Redis缓存
            cacheService.delete(keys);
            return;
        }

        /*
         当前处在数据库事务内：注册一个事务回调钩子
         不会立刻执行删除；等到事务commit提交成功之后，自动执行afterCommit()
         */
        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            // 事务提交成功后触发
                            @Override
                            public void afterCommit() {
                                cacheService.delete(
                                        keys
                                );
                            }
                        }
                );
    }
}

