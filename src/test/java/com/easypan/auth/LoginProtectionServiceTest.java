package com.easypan.auth;

import com.easypan.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginProtectionServiceTest {

    private StringRedisTemplate redisTemplate;

    private LoginProtectionService loginProtectionService;


    @BeforeEach
    void setUp() {

        redisTemplate =
                mock(StringRedisTemplate.class);

        /*
         * 配置：
         *
         * IP：
         * 60秒最多30次
         *
         * 密码：
         * 10分钟内连续失败5次
         * 锁定10分钟
         */
        loginProtectionService =
                new LoginProtectionService(
                        redisTemplate,
                        30,
                        60,
                        5,
                        600,
                        600
                );
    }


    /**
     * 第5次密码错误：
     *
     * Redis Lua 返回5
     * ↓
     * 说明达到最大失败次数
     * ↓
     * 账号进入临时锁定状态
     * ↓
     * 返回429
     */
    @Test
    void fifthPasswordFailureShouldLockAccount() {

        String username =
                "member1";


        /*
         * 模拟Lua脚本执行结果。
         *
         * 返回5代表：
         * 当前已经是第5次失败。
         */
        when(
                redisTemplate.execute(
                        ArgumentMatchers
                                .<RedisScript<Long>>any(),
                        anyList(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(5L);


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                loginProtectionService
                                        .recordPasswordFailure(
                                                username
                                        )
                );


        /*
         * 达到限制以后应该返回429。
         */
        assertEquals(
                429,
                exception.getCode()
        );


        assertEquals(
                "密码错误次数过多，请稍后再试",
                exception.getMessage()
        );


        /*
         * 验证确实执行了一次Redis Lua脚本。
         */
        verify(
                redisTemplate,
                times(1)
        ).execute(
                ArgumentMatchers
                        .<RedisScript<Long>>any(),

                eq(
                        List.of(
                                "simple-drive:security:login:failure:user:member1",
                                "simple-drive:security:login:lock:user:member1"
                        )
                ),

                eq("5"),
                eq("600"),
                eq("600")
        );
    }
    @Test
    void fourthPasswordFailureShouldNotLockAccount() {

        when(
                redisTemplate.execute(
                        ArgumentMatchers
                                .<RedisScript<Long>>any(),
                        anyList(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(4L);


        assertDoesNotThrow(
                () ->
                        loginProtectionService
                                .recordPasswordFailure(
                                        "member1"
                                )
        );
    }
    @Test
    void successfulPasswordShouldClearFailureCount() {

        String username = "member1";

        /*
         * 模拟密码正确后：
         *
         * AuthService会调用
         * clearPasswordFailures(username)
         */
        loginProtectionService
                .clearPasswordFailures(
                        username
                );


        /*
         * 应该删除：
         *
         * simple-drive:security:login:
         * failure:user:member1
         */
        verify(
                redisTemplate,
                times(1)
        ).delete(
                "simple-drive:security:login:failure:user:member1"
        );
    }
    @Test
    void lockedAccountShouldBeRejected() {

        String username = "member1";

        /*
         * 模拟Redis中已经存在：
         *
         * simple-drive:security:login:
         * lock:user:member1
         */
        when(
                redisTemplate.hasKey(
                        "simple-drive:security:login:lock:user:member1"
                )
        ).thenReturn(true);


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                loginProtectionService
                                        .checkPasswordLock(
                                                username
                                        )
                );


        assertEquals(
                429,
                exception.getCode()
        );

        assertEquals(
                "密码错误次数过多，请稍后再试",
                exception.getMessage()
        );


        /*
         * 验证确实检查了锁定Key。
         */
        verify(
                redisTemplate,
                times(1)
        ).hasKey(
                "simple-drive:security:login:lock:user:member1"
        );
    }
    @Test
    void unlockedAccountShouldPassPasswordLockCheck() {

        String username = "member1";


        when(
                redisTemplate.hasKey(
                        "simple-drive:security:login:lock:user:member1"
                )
        ).thenReturn(false);


        assertDoesNotThrow(
                () ->
                        loginProtectionService
                                .checkPasswordLock(
                                        username
                                )
        );


        verify(
                redisTemplate,
                times(1)
        ).hasKey(
                "simple-drive:security:login:lock:user:member1"
        );
    }
    @Test
    void requestWithinLimitShouldPass() {

        String ip = "127.0.0.1";


        /*
         * Lua返回30：
         *
         * 当前是第30次请求。
         * 仍然允许。
         */
        when(
                redisTemplate.execute(
                        ArgumentMatchers
                                .<RedisScript<Long>>any(),
                        anyList(),
                        any()
                )
        ).thenReturn(30L);


        assertDoesNotThrow(
                () ->
                        loginProtectionService
                                .checkRequestRate(ip)
        );
    }
    @Test
    void requestOverLimitShouldBeRejected() {

        String ip = "127.0.0.1";


        /*
         * Lua返回31：
         *
         * 已经超过30次限制。
         */
        when(
                redisTemplate.execute(
                        ArgumentMatchers
                                .<RedisScript<Long>>any(),
                        anyList(),
                        any()
                )
        ).thenReturn(31L);


        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                loginProtectionService
                                        .checkRequestRate(ip)
                );


        assertEquals(
                429,
                exception.getCode()
        );

        assertEquals(
                "登录请求过于频繁，请稍后再试",
                exception.getMessage()
        );
    }
}