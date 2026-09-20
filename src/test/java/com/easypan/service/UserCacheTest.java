package com.easypan.service;

import com.easypan.auth.CurrentUser;
import com.easypan.auth.LoginSessionService;
import com.easypan.auth.UserContext;
import com.easypan.cache.CacheInvalidationRegistrar;
import com.easypan.cache.CacheKeys;
import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
import com.easypan.mapper.DriveFileMapper;
import com.easypan.mapper.DriveFolderMapper;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.enums.Role;
import com.easypan.model.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCacheTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysDepartmentMapper departmentMapper;

    @Mock
    private DriveFolderMapper driveFolderMapper;

    @Mock
    private DriveFileMapper driveFileMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginSessionService loginSessionService;

    @Mock
    private RedisDataCacheService cacheService;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheInvalidationRegistrar cacheInvalidationRegistrar;

    @InjectMocks
    private UserService userService;

    private static final Duration USER_TTL =
            Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {

        /*
         * UserService.get() 有权限检查。
         *
         * 这里模拟管理员，
         * 管理员可以查看任意用户。
         */
        UserContext.set(
                new CurrentUser(
                        999L,
                        "admin",
                        "管理员",
                        Role.ADMIN,
                        null
                )
        );
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * Redis命中：
     *
     * Redis有数据
     * ↓
     * 直接返回
     * ↓
     * MySQL不能被查询
     */
    @Test
    void getShouldReturnCacheWithoutQueryingDatabase() {

        Long userId = 1L;

        String key =
                CacheKeys.userDetail(userId);

        UserVO cached =
                new UserVO(
                        1L,
                        "member1",
                        "测试用户",
                        Role.MEMBER.name(),
                        2L,
                        DataStatus.ACTIVE.name(),
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        1024L,
                        100L,
                        924L
                );

        when(
                cacheService.get(
                        key,
                        UserVO.class
                )
        ).thenReturn(cached);


        UserVO result =
                userService.get(userId);


        assertSame(
                cached,
                result
        );


        /*
         * 核心断言：
         *
         * 缓存命中后，
         * 绝对不能再查 sys_user。
         */
        verify(
                userMapper,
                never()
        ).selectById(anyLong());


        /*
         * 已经命中了，
         * 自然也不应该重新SET Redis。
         */
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
     * Redis未命中：
     *
     * Redis MISS
     * ↓
     * 查询MySQL
     * ↓
     * 转换UserVO
     * ↓
     * SET Redis
     */
    @Test
    void getShouldQueryDatabaseAndPopulateCacheWhenCacheMisses() {

        Long userId = 1L;

        String key =
                CacheKeys.userDetail(userId);

        when(
                cacheService.get(
                        key,
                        UserVO.class
                )
        ).thenReturn(null);

        when(
                cacheProperties.userDetailTtl()
        ).thenReturn(USER_TTL);


        SysUser dbUser =
                createUser(userId);

        when(
                userMapper.selectById(userId)
        ).thenReturn(dbUser);


        UserVO result =
                userService.get(userId);


        assertNotNull(result);

        assertEquals(
                userId,
                result.id()
        );

        assertEquals(
                "member1",
                result.username()
        );

        assertEquals(
                100L,
                result.usedBytes()
        );

        assertEquals(
                924L,
                result.remainingBytes()
        );


        /*
         * Redis MISS后，
         * MySQL必须查询一次。
         */
        verify(
                userMapper,
                times(1)
        ).selectById(userId);


        /*
         * 数据库查询成功后，
         * 必须回填Redis。
         */
        verify(
                cacheService,
                times(1)
        ).set(
                eq(key),
                eq(result),
                eq(USER_TTL)
        );
    }


    private SysUser createUser(Long id) {

        LocalDateTime now =
                LocalDateTime.now();

        SysUser user =
                new SysUser();

        user.setId(id);
        user.setUsername("member1");
        user.setRealName("测试用户");
        user.setRole(Role.MEMBER.name());
        user.setDepartmentId(2L);
        user.setStatus(DataStatus.ACTIVE.name());

        user.setQuotaBytes(1024L);
        user.setUsedBytes(100L);

        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return user;
    }
}