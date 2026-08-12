package com.easypan.auth;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionKickoutTest {

    private SysUserMapper userMapper;

    private JwtService jwtService;

    private AuthService authService;

    private AuthInterceptor authInterceptor;

    private SysUser user;

    @BeforeEach
    void setUp() {

        userMapper =
                mock(SysUserMapper.class);

        PasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder();

        /*
         * 至少32字节，
         * 满足HS256密钥长度要求。
         */
        jwtService =
                new JwtService(
                        "01234567890123456789012345678901",
                        3600
                );

        authService =
                new AuthService(
                        userMapper,
                        passwordEncoder,
                        jwtService
                );

        authInterceptor =
                new AuthInterceptor(
                        jwtService,
                        userMapper
                );

        /*
         * 模拟数据库中的用户。
         */
        user = new SysUser();

        user.setId(1L);
        user.setUsername("member1");
        user.setPassword(
                passwordEncoder.encode("123456")
        );
        user.setRealName("测试用户");
        user.setRole(Role.MEMBER.name());
        user.setDepartmentId(1L);
        user.setStatus(DataStatus.ACTIVE.name());

        /*
         * login查询用户名时，
         * 返回这个模拟用户。
         */
        when(
                userMapper.selectOne(any())
        ).thenReturn(user);

        /*
         * AuthInterceptor根据userId查用户。
         */
        when(
                userMapper.selectById(1L)
        ).thenReturn(user);

        /*
         * 模拟：
         *
         * UPDATE sys_user
         * SET current_session_id = ?
         *
         * 每次登录都覆盖user里的sessionId，
         * 模拟真实数据库行为。
         */
        doAnswer(invocation -> {

            String newSessionId =
                    invocation.getArgument(1);

            user.setCurrentSessionId(
                    newSessionId
            );

            return 1;

        }).when(userMapper)
                .replaceCurrentSession(
                        eq(1L),
                        anyString()
                );
    }

    @AfterEach
    void tearDown() {

        /*
         * 防止ThreadLocal污染其他测试。
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

        assertNotNull(firstToken);
        assertNotNull(firstSessionId);

        /*
         * 第一次登录后：
         *
         * JWT.sid
         * ==
         * DB.current_session_id
         */
        assertEquals(
                firstSessionId,
                user.getCurrentSessionId()
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
         * 两次登录的sessionId一定不同。
         */
        assertNotEquals(
                firstSessionId,
                secondSessionId
        );

        /*
         * 数据库现在应该保存第二次登录的sessionId。
         */
        assertEquals(
                secondSessionId,
                user.getCurrentSessionId()
        );


        /*
         * ============================
         * 使用第一次Token
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
         * 第一次Token已经被第二次登录踢下线。
         */
        assertEquals(
                401,
                exception.getCode()
        );

        assertEquals(
                "账号已在其他设备登录，请重新登录",
                exception.getMessage()
        );


        /*
         * ============================
         * 使用第二次Token
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
         * 第二次Token能够正确建立登录上下文。
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
     * 创建一个携带JWT的模拟HTTP请求。
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
