package com.easypan.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.*;

class CacheInvalidationRegistrarTest {

    private final RedisDataCacheService cacheService =
            mock(RedisDataCacheService.class);

    private final CacheInvalidationRegistrar registrar =
            new CacheInvalidationRegistrar(
                    cacheService
            );


    @AfterEach
    void tearDown() {

        /*
         * 防止事务同步状态污染其他测试。
         */
        if (
                TransactionSynchronizationManager
                        .isSynchronizationActive()
        ) {
            TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }


    /**
     * 验证核心原则：
     *
     * 数据库事务没有提交以前，
     * Redis缓存不能提前删除。
     */
    @Test
    void shouldDeleteCacheOnlyAfterTransactionCommit() {

        TransactionSynchronizationManager
                .initSynchronization();


        registrar.evictAfterCommit(
                "cache:user:1",
                "cache:department:list"
        );


        /*
         * 此时只是注册afterCommit。
         *
         * 事务还没commit，
         * Redis不能删除。
         */
        verify(
                cacheService,
                never()
        ).delete(
                any(String[].class)
        );


        List<TransactionSynchronization>
                synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();


        /*
         * 应该注册了一个事务回调。
         */
        assert synchronizations.size() == 1;


        /*
         * 模拟数据库事务提交成功。
         */
        synchronizations
                .forEach(
                        TransactionSynchronization
                                ::afterCommit
                );


        /*
         * COMMIT成功后，
         * Redis才真正删除。
         */
        verify(
                cacheService,
                times(1)
        ).delete(
                "cache:user:1",
                "cache:department:list"
        );
    }


    /**
     * 当前没有数据库事务时，
     * 可以直接删除缓存。
     */
    @Test
    void shouldDeleteImmediatelyWhenThereIsNoTransaction() {

        registrar.evictAfterCommit(
                "cache:user:1"
        );


        verify(
                cacheService,
                times(1)
        ).delete(
                "cache:user:1"
        );
    }
}