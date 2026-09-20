package com.easypan.auth;

import com.easypan.cache.CacheKeys;
import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.cache.CacheKey;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * JWT身份认证拦截器
 * 拦截受保护接口，校验请求携带的Bearer Token，完成用户身份解析、合法性校验，将当前登录用户存入线程上下文
 * 请求处理完毕后清理线程上下文，避免线程复用造成用户信息串扰
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final SysUserMapper userMapper;
    private final LoginSessionService loginSessionService;
    private final RedisDataCacheService cacheService;
    private final CacheProperties cacheProperties;

    /**
     * Controller执行之前执行的预处理方法
     * @param request Http请求对象
     * @param response Http响应对象
     * @param handler 处理器对象，代表即将执行的controller方法
     * @return true：放行请求；false：拦截请求；本项目校验失败直接抛出业务异常
     * Authorization Bearer
     *         ↓
     * 解析 JWT
     *         ↓
     * Redis Session 校验
     *         ↓
     * auth-user Redis
     *    ↓ HIT        ↓ MISS
     * CurrentUser    MySQL
     *                 ↓
     *              Redis SET
     *                 ↓
     *              CurrentUser
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {

        /*
         * 放行跨域 OPTIONS 预检请求。
         * OPTIONS 请求通常没有业务 Token。
         */
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        /*
         * 获取 Bearer Token。
         */
        String authorization = request.getHeader("Authorization");

        if (authorization == null|| !authorization.startsWith("Bearer ")) {
            throw new BusinessException(
                    401,
                    "请求头缺少Bearer Token"
            );
        }
        /*
         * 去掉 "Bearer " 前缀。
         */
        String token =
                authorization.substring(7);
        /*
         * 解析 JWT。
         *
         * Token过期、签名错误、被篡改
         * 都应该在这里失败。
         */
        JwtIdentity identity =
                jwtService.parse(token);
        /*
         * ==================================
         * 第一层：Redis Session 校验
         * ==================================
         *
         * 必须放在 AuthUser 缓存之前。
         *
         * 负责：
         * - 第二次登录踢掉旧Token
         * - logout后Token失效
         * - 重置密码后Token失效
         * - 禁用用户时Token失效
         */
        if (!loginSessionService.isCurrentSession(
                identity.userId(),
                identity.sessionId()
        )) {

            throw new BusinessException(
                    401,
                    "登录状态已失效，请重新登录"
            );
        }


        /*
         * 当前 session 已经确认有效。
         *
         * 再构造认证信息缓存Key。
         */
        String authCacheKey =
                CacheKeys.authUser(
                        identity.userId(),
                        identity.sessionId()
                );


        /*
         * ==================================
         * 第二层：AuthUser认证信息缓存
         * ==================================
         */
        CachedAuthUser authUser =
                cacheService.get(
                        authCacheKey,
                        CachedAuthUser.class
                );


        /*
         * Redis AuthUser MISS，
         * 才查询 MySQL。
         */
        if (authUser == null) {

            SysUser user =
                    userMapper.selectById(
                            identity.userId()
                    );


            /*
             * 数据库真实状态检查。
             *
             * 禁用用户绝对不能写入认证缓存。
             */
            if (user == null
                    || !DataStatus.ACTIVE.name()
                    .equals(user.getStatus())) {

                throw new BusinessException(
                        403,
                        "用户不存在或已被禁用"
                );
            }


            /*
             * SysUser
             * ↓
             * 精简成认证需要的数据。
             */
            authUser =
                    CachedAuthUser.from(user);


            /*
             * 回填 Redis。
             */
            cacheService.set(
                    authCacheKey,
                    authUser,
                    cacheProperties.authUserTtl()
            );
        }


        /*
         * 即使数据来自 Redis，
         * 也统一检查一次状态。
         */
        if (!authUser.isActive()) {

            throw new BusinessException(
                    403,
                    "用户不存在或已被禁用"
            );
        }


        /*
         * 建立本次请求的用户上下文。
         */
        UserContext.set(
                authUser.toCurrentUser(
                        identity.sessionId()
                )
        );


        /*
         * 校验全部通过。
         */
        return true;
    }

    /**
     * 请求完成之后回调，无论接口正常执行还是抛出异常，都会执行该方法
     * 核心作用：清除ThreadLocal保存的用户信息，防止线程池线程复用时，旧用户信息泄露到下一次请求
     * @param request Http请求对象
     * @param response Http响应对象
     * @param handler 处理器对象
     * @param exception 整个请求链路抛出的异常，没有异常则为null
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ){
        // 清空线程本地存储的登录用户信息，必须执行，防止内存泄漏、用户上下文错乱
        UserContext.clear();
    }

}