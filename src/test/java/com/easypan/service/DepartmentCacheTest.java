package com.easypan.service;

import com.easypan.cache.CacheInvalidationRegistrar;
import com.easypan.cache.CacheKeys;
import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.SysDepartment;
import com.easypan.model.enums.DataStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentCacheTest {

    @Mock
    private SysDepartmentMapper departmentMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private RedisDataCacheService cacheService;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheInvalidationRegistrar cacheInvalidationRegistrar;

    @InjectMocks
    private DepartmentService departmentService;

    private static final Duration DEPARTMENT_TTL =
            Duration.ofMinutes(2);


    /**
     * 部门详情：
     * Redis命中后不得查询数据库。
     */
    @Test
    void getShouldReturnCacheWithoutQueryingDatabase() {

        Long departmentId = 2L;

        String key =
                CacheKeys.departmentDetail(
                        departmentId
                );

        SysDepartment cached =
                createDepartment(
                        departmentId,
                        "技术部"
                );


        when(
                cacheService.get(
                        key,
                        SysDepartment.class
                )
        ).thenReturn(cached);


        SysDepartment result =
                departmentService.get(
                        departmentId
                );


        assertSame(
                cached,
                result
        );


        verify(
                departmentMapper,
                never()
        ).selectById(anyLong());


        verify(
                cacheService,
                never()
        ).set(
                anyString(),
                any(),
                any()
        );
    }


    /**
     * 部门详情：
     *
     * Redis未命中
     * ↓
     * 查MySQL
     * ↓
     * SET Redis
     */
    @Test
    void getShouldQueryDatabaseAndPopulateCacheWhenCacheMisses() {

        Long departmentId = 2L;

        String key =
                CacheKeys.departmentDetail(
                        departmentId
                );


        when(
                cacheService.get(
                        key,
                        SysDepartment.class
                )
        ).thenReturn(null);


        when(
                cacheProperties.departmentTtl()
        ).thenReturn(DEPARTMENT_TTL);


        SysDepartment databaseValue =
                createDepartment(
                        departmentId,
                        "技术部"
                );


        when(
                departmentMapper.selectById(
                        departmentId
                )
        ).thenReturn(databaseValue);


        SysDepartment result =
                departmentService.get(
                        departmentId
                );


        assertSame(
                databaseValue,
                result
        );


        verify(
                departmentMapper,
                times(1)
        ).selectById(departmentId);


        verify(
                cacheService,
                times(1)
        ).set(
                key,
                databaseValue,
                DEPARTMENT_TTL
        );
    }


    /**
     * 部门不存在时，
     * 不应该往Redis写入错误对象。
     */
    @Test
    void getShouldNotCacheWhenDepartmentDoesNotExist() {

        Long departmentId = 999L;

        String key =
                CacheKeys.departmentDetail(
                        departmentId
                );


        when(
                cacheService.get(
                        key,
                        SysDepartment.class
                )
        ).thenReturn(null);


        when(
                departmentMapper.selectById(
                        departmentId
                )
        ).thenReturn(null);


        assertThrows(
                RuntimeException.class,
                () ->
                        departmentService.get(
                                departmentId
                        )
        );


        verify(
                cacheService,
                never()
        ).set(
                anyString(),
                any(),
                any()
        );
    }


    /**
     * 部门列表Redis命中：
     * 不再执行selectList。
     */
    @Test
    void listShouldReturnCacheWithoutQueryingDatabase() {

        List<SysDepartment> cached =
                List.of(
                        createDepartment(
                                1L,
                                "技术部"
                        ),
                        createDepartment(
                                2L,
                                "市场部"
                        )
                );


        /*
         * 第二个参数是 TypeReference，
         * 所以这里用doReturn更容易处理泛型。
         */
        doReturn(cached)
                .when(cacheService)
                .get(
                        eq(
                                CacheKeys
                                        .DEPARTMENT_LIST
                        ),
                        ArgumentMatchers
                                .<TypeReference<
                                        List<SysDepartment>
                                        >>any()
                );


        List<SysDepartment> result =
                departmentService.list();


        assertSame(
                cached,
                result
        );


        verify(
                departmentMapper,
                never()
        ).selectList(any());


        verify(
                cacheService,
                never()
        ).set(
                anyString(),
                any(),
                any()
        );
    }


    /**
     * 部门列表缓存未命中：
     *
     * Redis MISS
     * ↓
     * MySQL selectList
     * ↓
     * SET Redis
     */
    @Test
    void listShouldQueryDatabaseAndPopulateCacheWhenCacheMisses() {

        List<SysDepartment> databaseValue =
                List.of(
                        createDepartment(
                                1L,
                                "技术部"
                        ),
                        createDepartment(
                                2L,
                                "市场部"
                        )
                );


        doReturn(null)
                .when(cacheService)
                .get(
                        eq(
                                CacheKeys
                                        .DEPARTMENT_LIST
                        ),
                        ArgumentMatchers
                                .<TypeReference<
                                        List<SysDepartment>
                                        >>any()
                );


        when(
                cacheProperties.departmentTtl()
        ).thenReturn(DEPARTMENT_TTL);


        when(
                departmentMapper.selectList(
                        any()
                )
        ).thenReturn(databaseValue);


        List<SysDepartment> result =
                departmentService.list();


        assertEquals(
                2,
                result.size()
        );


        verify(
                departmentMapper,
                times(1)
        ).selectList(any());


        verify(
                cacheService,
                times(1)
        ).set(
                CacheKeys.DEPARTMENT_LIST,
                databaseValue,
                DEPARTMENT_TTL
        );
    }


    private SysDepartment createDepartment(
            Long id,
            String name
    ) {

        LocalDateTime now =
                LocalDateTime.now();

        SysDepartment department =
                new SysDepartment();

        department.setId(id);
        department.setDepartmentName(name);

        department.setStatus(
                DataStatus.ACTIVE.name()
        );

        department.setQuotaBytes(
                10_000L
        );

        department.setUsedBytes(
                1_000L
        );

        department.setCreatedAt(now);
        department.setUpdatedAt(now);

        return department;
    }
}