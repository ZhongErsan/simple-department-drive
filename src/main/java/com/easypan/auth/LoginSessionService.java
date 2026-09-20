package com.easypan.auth;

import com.easypan.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;

/**
 * 登录会话管理服务
 * 功能：基于Redis实现单点登录控制，同一个用户只保留最新一次有效会话
 * 原理：Redis存储【用户ID -> 当前有效sessionId】，新登录直接覆盖旧会话，实现挤下线；会话自带过期时间
 */
@Service
public class LoginSessionService {

    /**
     * Lua脚本：原子执行【判断值相等 + 删除key】
     * 业务场景：用户主动退出登录，只有redis中存储的sessionId和传入的sessionId一致，才删除这条会话记录
     * 为什么用Lua：保证get+del是一条原子操作，防止高并发下竞态问题（查询和删除之间数据被修改）
     * KEYS[1]：redis的key（用户会话key）
     * ARGV[1]：待比对的sessionId
     * 返回值：1=匹配成功并删除；0=值不匹配，不执行删除
     */
    private static final DefaultRedisScript<Long> DELETE_IF_MATCH_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] " +
                            "then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    // Spring提供的Redis操作模板，专门操作String类型的key-value
    private final StringRedisTemplate redisTemplate;
    // 会话过期时长，从配置文件读取，和JWT的过期时间保持一致
    private final Duration sessionTtl;
    // Redis key前缀，配置项，默认 simple-drive:auth:session:
    private final String keyPrefix;

    /**
     * 构造注入
     * @param redisTemplate Redis操作模板
     * @param expirationSeconds JWT过期秒数，作为Redis会话key的过期时间
     * @param keyPrefix Redis会话key前缀
     */
    public LoginSessionService(
            StringRedisTemplate redisTemplate,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds,
            @Value("${app.auth.redis-session-prefix:simple-drive:auth:session:}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.sessionTtl = Duration.ofSeconds(expirationSeconds);
        this.keyPrefix = keyPrefix;
    }

    /**
     * 替换用户当前会话（用户登录成功时调用）
     * 新会话直接覆盖Redis里旧的sessionId，旧设备会话直接失效，实现单点登录挤下线
     * @param userId 用户ID
     * @param sessionId 本次登录生成的会话ID
     */
    public void replaceSession(Long userId, String sessionId) {
        try {
            // 设置key=用户会话key，value=sessionId，同时设置过期时间
            redisTemplate.opsForValue().set(
                    key(userId),
                    sessionId,
                    sessionTtl
            );
        } catch (DataAccessException e) {
            // Redis发生异常（连接失败、网络问题等），抛自定义业务异常
            throw new BusinessException(
                    503,
                    "登录会话服务暂不可用，请稍后重试"
            );
        }
    }

    /**
     * 校验当前请求携带的sessionId，是否是该用户Redis中保存的有效会话
     * 鉴权接口时调用，用来判断当前token是否是最新登录的会话
     * @param userId 用户ID
     * @param sessionId 请求携带的sessionId
     * @return true：会话有效；false：会话无效（旧会话，已被挤下线）
     */
    public boolean isCurrentSession(
            Long userId,
            String sessionId
    ) {
        try {
            // 从Redis取出该用户保存的有效sessionId
            String currentSessionId =
                    redisTemplate.opsForValue().get(key(userId));
            // 比较Redis存储的值和请求传入的sessionId是否完全一致
            return Objects.equals(
                    currentSessionId,
                    sessionId
            );
        } catch (DataAccessException e) {
            throw new BusinessException(
                    503,
                    "登录会话服务暂不可用，请稍后重试"
            );
        }
    }

    /**
     * 用户主动退出登录：匹配成功才删除会话记录（Lua原子脚本）
     * 防止出现：A设备退出登录，误删除B设备正在使用的会话
     * @param userId 用户ID
     * @param sessionId 当前登录设备携带的sessionId
     * @return true 删除成功；false 不匹配，删除失败
     */
    public boolean removeSessionIfMatch(
            Long userId,
            String sessionId
    ) {
        try {
            // 执行Lua脚本，Collections.singletonList封装KEYS，第二个参数是ARGV参数
            Long result = redisTemplate.execute(
                    DELETE_IF_MATCH_SCRIPT,
                    Collections.singletonList(key(userId)),
                    sessionId
            );
            // 返回1代表脚本执行成功，key匹配并且删除
            return Long.valueOf(1L).equals(result);
        } catch (DataAccessException e) {
            throw new BusinessException(
                    503,
                    "登录会话服务暂不可用，请稍后重试"
            );
        }
    }

    /**
     * 强制使该用户所有会话失效
     * 使用场景：管理员强制踢人、修改密码，不管当前session是什么，直接删掉Redis记录
     * @param userId 用户ID
     */
    public void invalidate(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException e) {
            throw new BusinessException(
                    503,
                    "登录会话服务暂不可用，请稍后重试"
            );
        }
    }

    /**
     * 私有工具方法：拼接完整Redis Key
     * @param userId 用户ID
     * @return 拼接后的key，例：simple-drive:auth:session:10001
     */
    private String key(Long userId) {
        return keyPrefix + userId;
    }
}
