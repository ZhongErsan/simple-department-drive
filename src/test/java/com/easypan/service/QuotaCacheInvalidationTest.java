package com.easypan.service;

import com.easypan.cache.CacheInvalidationRegistrar;
import com.easypan.cache.CacheKeys;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.enums.AreaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaCacheInvalidationTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysDepartmentMapper departmentMapper;

    @Mock
    private CacheInvalidationRegistrar
            cacheInvalidationRegistrar;

    @InjectMocks
    private QuotaService quotaService;


    /**
     * 个人空间占用容量成功后，
     * 用户详情缓存必须失效。
     */
    @Test
    void consumePersonalQuotaShouldEvictUserCache() {

        Long userId = 10L;

        DriveFolder folder =
                personalFolder(
                        userId,
                        2L
                );


        when(
                userMapper.tryConsumeQuota(
                        userId,
                        100L
                )
        ).thenReturn(1);


        quotaService.consume(
                folder,
                100L
        );


        verify(
                userMapper,
                times(1)
        ).tryConsumeQuota(
                userId,
                100L
        );


        verify(
                cacheInvalidationRegistrar,
                times(1)
        ).evictAfterCommit(
                CacheKeys.userDetail(
                        userId
                )
        );
    }


    /**
     * 个人空间释放容量成功后，
     * 用户详情缓存必须失效。
     */
    @Test
    void releasePersonalQuotaShouldEvictUserCache() {

        Long userId = 10L;

        DriveFolder folder =
                personalFolder(
                        userId,
                        2L
                );


        when(
                userMapper.releaseQuota(
                        userId,
                        100L
                )
        ).thenReturn(1);


        quotaService.release(
                folder,
                100L
        );


        verify(
                userMapper,
                times(1)
        ).releaseQuota(
                userId,
                100L
        );


        verify(
                cacheInvalidationRegistrar,
                times(1)
        ).evictAfterCommit(
                CacheKeys.userDetail(
                        userId
                )
        );
    }


    /**
     * 部门空间占用容量成功后：
     *
     * 1. 部门详情缓存失效
     * 2. 部门列表缓存失效
     *
     * 因为两个地方都有usedBytes。
     */
    @Test
    void consumeDepartmentQuotaShouldEvictDepartmentCaches() {

        Long departmentId = 2L;

        DriveFolder folder =
                departmentFolder(
                        departmentId
                );


        when(
                departmentMapper
                        .tryConsumeQuota(
                                departmentId,
                                100L
                        )
        ).thenReturn(1);


        quotaService.consume(
                folder,
                100L
        );


        verify(
                departmentMapper,
                times(1)
        ).tryConsumeQuota(
                departmentId,
                100L
        );


        verify(
                cacheInvalidationRegistrar,
                times(1)
        ).evictAfterCommit(
                CacheKeys.departmentDetail(
                        departmentId
                ),
                CacheKeys.DEPARTMENT_LIST
        );
    }


    /**
     * 部门释放容量成功后，
     * 两个部门缓存都必须失效。
     */
    @Test
    void releaseDepartmentQuotaShouldEvictDepartmentCaches() {

        Long departmentId = 2L;

        DriveFolder folder =
                departmentFolder(
                        departmentId
                );


        when(
                departmentMapper
                        .releaseQuota(
                                departmentId,
                                100L
                        )
        ).thenReturn(1);


        quotaService.release(
                folder,
                100L
        );


        verify(
                departmentMapper,
                times(1)
        ).releaseQuota(
                departmentId,
                100L
        );


        verify(
                cacheInvalidationRegistrar,
                times(1)
        ).evictAfterCommit(
                CacheKeys.departmentDetail(
                        departmentId
                ),
                CacheKeys.DEPARTMENT_LIST
        );
    }


    /**
     * 非常重要：
     *
     * 如果个人配额更新失败，
     * 不应该执行缓存失效。
     *
     * 因为MySQL数据根本没有发生变化。
     */
    @Test
    void failedPersonalQuotaConsumeShouldNotEvictCache() {

        Long userId = 10L;

        DriveFolder folder =
                personalFolder(
                        userId,
                        2L
                );


        when(
                userMapper.tryConsumeQuota(
                        userId,
                        100L
                )
        ).thenReturn(0);


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                quotaService.consume(
                                        folder,
                                        100L
                                )
                );


        assertEquals(
                413,
                exception.getCode()
        );


        /*
         * 数据库更新失败，
         * 绝对不能注册缓存删除。
         */
        verifyNoInteractions(
                cacheInvalidationRegistrar
        );
    }


    /**
     * 部门配额不足时也不能误删缓存。
     */
    @Test
    void failedDepartmentQuotaConsumeShouldNotEvictCache() {

        Long departmentId = 2L;

        DriveFolder folder =
                departmentFolder(
                        departmentId
                );


        when(
                departmentMapper
                        .tryConsumeQuota(
                                departmentId,
                                100L
                        )
        ).thenReturn(0);


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                quotaService.consume(
                                        folder,
                                        100L
                                )
                );


        assertEquals(
                413,
                exception.getCode()
        );


        verifyNoInteractions(
                cacheInvalidationRegistrar
        );
    }


    private DriveFolder personalFolder(
            Long ownerId,
            Long departmentId
    ) {

        DriveFolder folder =
                new DriveFolder();

        folder.setId(100L);

        folder.setAreaType(
                AreaType.PERSONAL.name()
        );

        folder.setOwnerId(
                ownerId
        );

        folder.setDepartmentId(
                departmentId
        );

        return folder;
    }


    private DriveFolder departmentFolder(
            Long departmentId
    ) {

        DriveFolder folder =
                new DriveFolder();

        folder.setId(200L);

        /*
         * PUBLIC和CONTRIBUTION都会走
         * 部门配额。
         */
        folder.setAreaType(
                AreaType.PUBLIC.name()
        );

        folder.setDepartmentId(
                departmentId
        );

        return folder;
    }
}