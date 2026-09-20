package com.easypan.auth;

import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.dto.LoginRequest;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.enums.Role;
import com.easypan.model.vo.LoginResponse;
import com.easypan.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionKickoutTest {

    private SysUserMapper userMapper;

    private JwtService jwtService;

    private LoginSessionService loginSessionService;

    private AuthService authService;

    private AuthInterceptor authInterceptor;

    private SysUser user;
    private RedisDataCacheService cacheService;
    private CacheProperties cacheProperties;

    private LoginProtectionService loginProtectionService;

    /**
     * 用来模拟 Redis 中：
     *
     * simple-drive:auth:session:1
     *
     * 当前保存的 sessionId。
     */
    private AtomicReference<String> redisSession;

    @BeforeEach
    void setUp() {

        userMapper =
                mock(SysUserMapper.class);

        loginSessionService =
                mock(LoginSessionService.class);

        redisSession =
                new AtomicReference<>();

        PasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder();
        cacheService =
                mock(RedisDataCacheService.class);

        cacheProperties =
                mock(CacheProperties.class);
        loginProtectionService =
                mock(LoginProtectionService.class);
        when(
                cacheProperties.authUserTtl()
        ).thenReturn(
                Duration.ofMinutes(2)
        );
        /*
         * 至少32字节，
         * 满足HS256密钥长度要求。
         */
        jwtService =
                new JwtService(
                        "01234567890123456789012345678901",
                        3600
                );

        /*
         * AuthService 现在需要 Redis Session Service。
         */
        authService =
                new AuthService(
                        userMapper,
                        passwordEncoder,
                        jwtService,
                        loginSessionService,
                        cacheService,
                        cacheProperties,
                        loginProtectionService
                );

        /*
         * AuthInterceptor 现在也需要 Redis Session Service。
         */
        authInterceptor =
                new AuthInterceptor(
                        jwtService,
                        userMapper,
                        loginSessionService,
                        cacheService,
                        cacheProperties
                );

        /*
         * 模拟数据库用户。
         *
         * 注意：
         * currentSessionId 已经不需要了。
         */
        user = new SysUser();

        user.setId(1L);
        user.setUsername("member1");

        user.setPassword(
                passwordEncoder.encode("123456")
        );

        user.setRealName("测试用户");

        user.setRole(
                Role.MEMBER.name()
        );

        user.setDepartmentId(1L);

        user.setStatus(
                DataStatus.ACTIVE.name()
        );

        /*
         * 登录时根据 username 查询用户。
         */
        when(
                userMapper.selectOne(any())
        ).thenReturn(user);

        /*
         * AuthInterceptor Redis 校验通过之后，
         * 根据 userId 查询用户。
         */
        when(
                userMapper.selectById(1L)
        ).thenReturn(user);


        /*
         * ============================
         * 模拟 Redis SET
         * ============================
         *
         * loginSessionService.replaceSession(
         *      userId,
         *      sessionId
         * )
         *
         * 每次登录都会覆盖 Redis 中原来的 sessionId。
         */
        doAnswer(invocation -> {

            String sessionId =
                    invocation.getArgument(1);

            redisSession.set(
                    sessionId
            );

            return null;

        }).when(loginSessionService)
                .replaceSession(
                        eq(1L),
                        anyString()
                );


        /*
         * ============================
         * 模拟 Redis GET + 比较
         * ============================
         *
         * Redis 当前 sessionId
         * ==
         * JWT 中的 sessionId
         *
         * 才认为登录有效。
         */
        when(
                loginSessionService.isCurrentSession(
                        eq(1L),
                        anyString()
                )
        ).thenAnswer(invocation -> {

            String tokenSessionId =
                    invocation.getArgument(1);

            return Objects.equals(
                    redisSession.get(),
                    tokenSessionId
            );
        });
    }


    @AfterEach
    void tearDown() {

        /*
         * 防止 ThreadLocal 污染其他测试。
         */
        UserContext.clear();
    }


    @Test
    void secondLoginShouldKickFirstTokenOffline()
            throws Exception {

        /*
         * ============================
         * 第一次登录
         * ============================
         */

        LoginResponse firstLogin =
                authService.login(
                        new LoginRequest(
                                "member1",
                                "123456"
                        )
                );

        String firstToken =
                firstLogin.token();

        JwtIdentity firstIdentity =
                jwtService.parse(
                        firstToken
                );

        String firstSessionId =
                firstIdentity.sessionId();

        assertNotNull(
                firstToken
        );

        assertNotNull(
                firstSessionId
        );


        /*
         * 第一次登录之后：
         *
         * Redis:
         *
         * simple-drive:auth:session:1
         * =
         * firstSessionId
         */
        assertEquals(
                firstSessionId,
                redisSession.get()
        );


        /*
         * ============================
         * 第二次登录
         * ============================
         */

        LoginResponse secondLogin =
                authService.login(
                        new LoginRequest(
                                "member1",
                                "123456"
                        )
                );

        String secondToken =
                secondLogin.token();

        JwtIdentity secondIdentity =
                jwtService.parse(
                        secondToken
                );

        String secondSessionId =
                secondIdentity.sessionId();


        /*
         * 每次登录都会生成新的 sessionId。
         */
        assertNotEquals(
                firstSessionId,
                secondSessionId
        );


        /*
         * Redis 中的旧 session 已经被覆盖。
         *
         * Redis:
         *
         * simple-drive:auth:session:1
         * =
         * secondSessionId
         */
        assertEquals(
                secondSessionId,
                redisSession.get()
        );


        /*
         * ============================
         * 使用第一次 Token 请求
         * ============================
         */

        MockHttpServletRequest firstRequest =
                requestWithToken(
                        firstToken
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                authInterceptor.preHandle(
                                        firstRequest,
                                        response,
                                        new Object()
                                )
                );


        /*
         * 第一次 Token 中：
         *
         * sid = firstSessionId
         *
         * Redis 中：
         *
         * sid = secondSessionId
         *
         * 所以 Redis 校验失败。
         */
        assertEquals(
                401,
                exception.getCode()
        );

        assertEquals(
                "登录状态已失效，请重新登录",
                exception.getMessage()
        );


        /*
         * 很重要：
         *
         * Redis 校验失败之后，
         * 不应该继续查 MySQL。
         */
        verify(
                userMapper,
                never()
        ).selectById(1L);


        /*
         * ============================
         * 使用第二次 Token
         * ============================
         */

        MockHttpServletRequest secondRequest =
                requestWithToken(
                        secondToken
                );


        assertTrue(
                authInterceptor.preHandle(
                        secondRequest,
                        response,
                        new Object()
                )
        );


        /*
         * Redis 验证成功后，
         * 才会查询数据库。
         */
        verify(
                userMapper,
                times(1)
        ).selectById(1L);


        /*
         * 第二次 Token 能够正常建立 UserContext。
         */
        CurrentUser currentUser =
                UserContext.require();


        assertEquals(
                1L,
                currentUser.userId()
        );


        assertEquals(
                secondSessionId,
                currentUser.sessionId()
        );


        /*
         * 模拟请求结束。
         */
        authInterceptor.afterCompletion(
                secondRequest,
                response,
                new Object(),
                null
        );
    }


    /**
     * 创建携带 JWT 的模拟 HTTP 请求。
     */
    private MockHttpServletRequest requestWithToken(
            String token
    ) {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setMethod(
                "GET"
        );

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        return request;
    }
}