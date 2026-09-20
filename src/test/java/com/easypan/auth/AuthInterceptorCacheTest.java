package com.easypan.auth;

import com.easypan.cache.CacheKeys;
import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthInterceptorCacheTest {

    private JwtService jwtService;

    private SysUserMapper userMapper;

    private LoginSessionService loginSessionService;

    private RedisDataCacheService cacheService;

    private CacheProperties cacheProperties;

    private AuthInterceptor authInterceptor;


    private static final Long USER_ID = 1L;

    private static final String SESSION_ID =
            "session-001";

    private static final String TOKEN =
            "token-001";

    private static final Duration AUTH_TTL =
            Duration.ofMinutes(2);


    @BeforeEach
    void setUp() {

        jwtService =
                mock(JwtService.class);

        userMapper =
                mock(SysUserMapper.class);

        loginSessionService =
                mock(LoginSessionService.class);

        cacheService =
                mock(RedisDataCacheService.class);

        cacheProperties =
                mock(CacheProperties.class);


        authInterceptor =
                new AuthInterceptor(
                        jwtService,
                        userMapper,
                        loginSessionService,
                        cacheService,
                        cacheProperties
                );


        /*
         * 所有正常测试：
         *
         * JWT解析后得到：
         * userId = 1
         * sessionId = session-001
         */
        when(
                jwtService.parse(TOKEN)
        ).thenReturn(
                new JwtIdentity(
                        USER_ID,
                        SESSION_ID
                )
        );
    }


    @AfterEach
    void tearDown() {

        /*
         * 防止 ThreadLocal 污染其他测试。
         */
        UserContext.clear();
    }


    /**
     * 场景1：
     *
     * Session有效
     * +
     * AuthUser缓存命中
     *
     * 结果：
     * 不允许查询sys_user数据库。
     */
    @Test
    void authCacheHitShouldNotQueryDatabase()
            throws Exception {

        /*
         * Redis Session有效。
         */
        when(
                loginSessionService
                        .isCurrentSession(
                                USER_ID,
                                SESSION_ID
                        )
        ).thenReturn(true);


        String cacheKey =
                CacheKeys.authUser(
                        USER_ID,
                        SESSION_ID
                );


        CachedAuthUser cachedUser =
                new CachedAuthUser(
                        USER_ID,
                        "member1",
                        "测试用户",
                        Role.MEMBER.name(),
                        2L,
                        DataStatus.ACTIVE.name()
                );


        /*
         * Redis AuthUser缓存命中。
         */
        when(
                cacheService.get(
                        cacheKey,
                        CachedAuthUser.class
                )
        ).thenReturn(cachedUser);


        MockHttpServletRequest request =
                requestWithToken(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();


        boolean result =
                authInterceptor.preHandle(
                        request,
                        response,
                        new Object()
                );


        assertTrue(result);


        /*
         * =========================
         * 核心断言
         * =========================
         *
         * Auth缓存命中以后，
         * 绝对不能再查询sys_user。
         */
        verify(
                userMapper,
                never()
        ).selectById(anyLong());


        /*
         * 已经命中Redis，
         * 不需要重新写缓存。
         */
        verify(
                cacheService,
                never()
        ).set(
                anyString(),
                any(),
                any()
        );


        /*
         * UserContext应该正确建立。
         */
        CurrentUser currentUser =
                UserContext.require();


        assertEquals(
                USER_ID,
                currentUser.userId()
        );

        assertEquals(
                "member1",
                currentUser.username()
        );

        assertEquals(
                "测试用户",
                currentUser.realName()
        );

        assertEquals(
                Role.MEMBER,
                currentUser.role()
        );

        assertEquals(
                2L,
                currentUser.departmentId()
        );

        assertEquals(
                SESSION_ID,
                currentUser.sessionId()
        );
    }


    /**
     * 场景2：
     *
     * Session有效
     * +
     * AuthUser缓存MISS
     *
     * 结果：
     *
     * 查MySQL一次
     * ↓
     * 写Redis
     * ↓
     * 建立UserContext
     */
    @Test
    void authCacheMissShouldQueryDatabaseAndPopulateCache()
            throws Exception {

        when(
                loginSessionService
                        .isCurrentSession(
                                USER_ID,
                                SESSION_ID
                        )
        ).thenReturn(true);


        String cacheKey =
                CacheKeys.authUser(
                        USER_ID,
                        SESSION_ID
                );


        /*
         * Redis未命中。
         */
        when(
                cacheService.get(
                        cacheKey,
                        CachedAuthUser.class
                )
        ).thenReturn(null);


        /*
         * 模拟MySQL用户。
         */
        SysUser databaseUser =
                createActiveUser();


        when(
                userMapper.selectById(
                        USER_ID
                )
        ).thenReturn(databaseUser);


        when(
                cacheProperties.authUserTtl()
        ).thenReturn(AUTH_TTL);


        MockHttpServletRequest request =
                requestWithToken(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();


        boolean result =
                authInterceptor.preHandle(
                        request,
                        response,
                        new Object()
                );


        assertTrue(result);


        /*
         * Redis MISS以后，
         * MySQL应该只查询一次。
         */
        verify(
                userMapper,
                times(1)
        ).selectById(USER_ID);


        /*
         * 期望写入Redis的数据。
         */
        CachedAuthUser expected =
                new CachedAuthUser(
                        USER_ID,
                        "member1",
                        "测试用户",
                        Role.MEMBER.name(),
                        2L,
                        DataStatus.ACTIVE.name()
                );


        /*
         * 数据库查询成功后，
         * 应该回填Redis。
         */
        verify(
                cacheService,
                times(1)
        ).set(
                cacheKey,
                expected,
                AUTH_TTL
        );


        /*
         * UserContext也必须正确建立。
         */
        CurrentUser currentUser =
                UserContext.require();


        assertEquals(
                USER_ID,
                currentUser.userId()
        );

        assertEquals(
                Role.MEMBER,
                currentUser.role()
        );

        assertEquals(
                2L,
                currentUser.departmentId()
        );

        assertEquals(
                SESSION_ID,
                currentUser.sessionId()
        );
    }


    /**
     * 场景3：
     *
     * Redis Session已经失效。
     *
     * 必须立即401。
     *
     * 注意：
     * 连AuthUser缓存都不应该继续查询，
     * 更不能查询MySQL。
     */
    @Test
    void invalidSessionShouldStopBeforeReadingAuthCache() {

        when(
                loginSessionService
                        .isCurrentSession(
                                USER_ID,
                                SESSION_ID
                        )
        ).thenReturn(false);


        MockHttpServletRequest request =
                requestWithToken(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                authInterceptor.preHandle(
                                        request,
                                        response,
                                        new Object()
                                )
                );


        assertEquals(
                401,
                exception.getCode()
        );

        assertEquals(
                "登录状态已失效，请重新登录",
                exception.getMessage()
        );


        /*
         * Session都已经无效了，
         * 不应该继续读取AuthUser缓存。
         */
        verify(
                cacheService,
                never()
        ).get(
                anyString(),
                eq(CachedAuthUser.class)
        );


        /*
         * 更不能查询MySQL。
         */
        verify(
                userMapper,
                never()
        ).selectById(anyLong());
    }


    /**
     * 场景4：
     *
     * 如果Redis里面意外存在DISABLED用户，
     * 也不能直接放行。
     *
     * 这可以验证：
     * 缓存命中以后仍然执行status校验。
     */
    @Test
    void disabledCachedUserShouldBeRejected() {

        when(
                loginSessionService
                        .isCurrentSession(
                                USER_ID,
                                SESSION_ID
                        )
        ).thenReturn(true);


        String cacheKey =
                CacheKeys.authUser(
                        USER_ID,
                        SESSION_ID
                );


        CachedAuthUser disabledUser =
                new CachedAuthUser(
                        USER_ID,
                        "member1",
                        "测试用户",
                        Role.MEMBER.name(),
                        2L,
                        DataStatus.DISABLED.name()
                );


        when(
                cacheService.get(
                        cacheKey,
                        CachedAuthUser.class
                )
        ).thenReturn(disabledUser);


        MockHttpServletRequest request =
                requestWithToken(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                authInterceptor.preHandle(
                                        request,
                                        response,
                                        new Object()
                                )
                );


        assertEquals(
                403,
                exception.getCode()
        );


        /*
         * Redis已经有数据，
         * 不应该再查询数据库。
         */
        verify(
                userMapper,
                never()
        ).selectById(anyLong());
    }


    /**
     * 场景5：
     *
     * Auth缓存MISS，
     * 数据库发现用户已经禁用。
     *
     * 结果：
     * 403，并且绝不能把禁用用户放入Redis。
     */
    @Test
    void disabledDatabaseUserShouldNotBeCached() {

        when(
                loginSessionService
                        .isCurrentSession(
                                USER_ID,
                                SESSION_ID
                        )
        ).thenReturn(true);


        String cacheKey =
                CacheKeys.authUser(
                        USER_ID,
                        SESSION_ID
                );


        when(
                cacheService.get(
                        cacheKey,
                        CachedAuthUser.class
                )
        ).thenReturn(null);


        SysUser disabledUser =
                createActiveUser();

        disabledUser.setStatus(
                DataStatus.DISABLED.name()
        );


        when(
                userMapper.selectById(
                        USER_ID
                )
        ).thenReturn(disabledUser);


        MockHttpServletRequest request =
                requestWithToken(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                authInterceptor.preHandle(
                                        request,
                                        response,
                                        new Object()
                                )
                );


        assertEquals(
                403,
                exception.getCode()
        );


        /*
         * 确实查询了一次数据库。
         */
        verify(
                userMapper,
                times(1)
        ).selectById(USER_ID);


        /*
         * 禁用用户绝对不能写入认证缓存。
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
     * 创建数据库中的正常用户。
     */
    private SysUser createActiveUser() {

        SysUser user =
                new SysUser();

        user.setId(USER_ID);

        user.setUsername(
                "member1"
        );

        user.setRealName(
                "测试用户"
        );

        user.setRole(
                Role.MEMBER.name()
        );

        user.setDepartmentId(
                2L
        );

        user.setStatus(
                DataStatus.ACTIVE.name()
        );

        return user;
    }


    /**
     * 创建携带Bearer Token的请求。
     */
    private MockHttpServletRequest requestWithToken(
            String token
    ) {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setMethod("GET");

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        return request;
    }
}