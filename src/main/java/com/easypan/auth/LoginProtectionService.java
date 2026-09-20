package com.easypan.auth;

import com.easypan.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class LoginProtectionService {
    //请求频率限制
    private static final DefaultRedisScript<Long> REQUEST_RATE_SCRIPT=
            new DefaultRedisScript<>(
                    """
                            local current = redis.call('INCR', KEYS[1])
                            
                                               if current == 1 then
                                                   redis.call('EXPIRE', KEYS[1], ARGV[1])
                                               end
                            
                                               return current
                            """,
                    Long.class
            );
    //密码连续失败
    private static final DefaultRedisScript<Long> PASSWORD_FAILURE_SCRIPT=
            new DefaultRedisScript<>(
                    """
                            if redis.call('EXISTS', KEYS[2]) == 1 then
                                                    return -1
                                                end
                            
                                                local current =
                                                    redis.call('INCR', KEYS[1])
                            
                                                if current == 1 then
                                                    redis.call(
                                                        'EXPIRE',
                                                        KEYS[1],
                                                        ARGV[2]
                                                    )
                                                end
                            
                                                if current >= tonumber(ARGV[1]) then
                            
                                                    redis.call(
                                                        'SET',
                                                        KEYS[2],
                                                        '1',
                                                        'EX',
                                                        ARGV[3]
                                                    )
                            
                                                    redis.call(
                                                        'DEL',
                                                        KEYS[1]
                                                    )
                                                end
                            
                                                return current
                            """,
                    Long.class
            );
    private static final String PREFIX =
            "simple-drive:security:login:";


    private final StringRedisTemplate redisTemplate;

    private final int requestLimit;

    private final int requestWindowSeconds;

    private final int maxPasswordFailures;

    private final int failureWindowSeconds;

    private final int lockSeconds;


    public LoginProtectionService(
            StringRedisTemplate redisTemplate,

            @Value("${app.login-protection.request-limit:30}")
            int requestLimit,

            @Value("${app.login-protection.request-window-seconds:60}")
            int requestWindowSeconds,

            @Value("${app.login-protection.max-password-failures:5}")
            int maxPasswordFailures,

            @Value("${app.login-protection.failure-window-seconds:600}")
            int failureWindowSeconds,

            @Value("${app.login-protection.lock-seconds:600}")
            int lockSeconds
    ) {

        this.redisTemplate =
                redisTemplate;

        this.requestLimit =
                requestLimit;

        this.requestWindowSeconds =
                requestWindowSeconds;

        this.maxPasswordFailures =
                maxPasswordFailures;

        this.failureWindowSeconds =
                failureWindowSeconds;

        this.lockSeconds =
                lockSeconds;
    }
    //第一层：登录接口请求频率限制，按IP
    public void checkRequestRate(String ip){
        String key=PREFIX+"rate:ip"+ip;
        try{
            Long count=redisTemplate.execute(REQUEST_RATE_SCRIPT, List.of(key),String.valueOf(requestWindowSeconds));
            if(count!=null&&count>requestLimit)
                throw new BusinessException(429,"登录请求过于频繁，请稍后再试");
        }catch (DataAccessException e){
            throw new BusinessException(503,"登录服务暂不可用，请稍后重试");
        }
    }
    //第二层，验证密码之前检查：当前用户名是否已经因为连续密码错误被锁定
    public void checkPasswordLock(String username){
        String normalized=normalizeUsername(username);
        String key=lockKey(normalized);
        try{
            Boolean locked=redisTemplate.hasKey(key);
            if(Boolean.TRUE.equals(locked)){
                throw new BusinessException(429,"密码错误次数过多，请稍后再试");
            }
        }catch (DataAccessException e){
            throw new BusinessException(503,"登录服务暂不可用，请稍后重试");
        }
    }
    public void recordPasswordFailure(String username){
        String normalized=normalizeUsername(username);
        String failureKey=failureKey(normalized);
        String lockKey=lockKey(normalized);
        try{
            Long result=redisTemplate.execute(
                    PASSWORD_FAILURE_SCRIPT,
                    List.of(failureKey,lockKey),
                    String.valueOf(maxPasswordFailures),
                    String.valueOf(failureWindowSeconds),
                    String.valueOf(lockSeconds)
            );
            //已经锁定
            if(result!=null&&(result==-1||result>=maxPasswordFailures)){
                throw new BusinessException(429,"密码错误次数过多，请稍后再试");
            }
        }catch (DataAccessException e){
            throw new BusinessException(503,"登录服务暂不可用，请稍后重试");
        }
    }
    //密码正确后，连续失败次数立即清0
    public void clearPasswordFailures(String username){
        String normalized=normalizeUsername(username);
        try{
            redisTemplate.delete(failureKey(normalized));
        }catch (DataAccessException e){
            throw new BusinessException(503,"登录服务暂不可用，请稍后重试");
        }
    }
    private String failureKey(String username){
        return PREFIX+"failure:user:"+username;
    }
    private String lockKey(String username){
        return PREFIX+"lock:user:"+username;
    }

    private String normalizeUsername(String username){
        return username.trim().toLowerCase(Locale.ROOT);
    }


}
