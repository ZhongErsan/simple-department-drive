package com.easypan.auth;

import com.easypan.exception.BusinessException;
import com.easypan.model.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT工具服务
 * 负责登录令牌的生成、解析与签名校验
 * 使用HS256对称加密算法，签发包含用户身份信息的Token；
 * 拦截非法、篡改、过期令牌，统一抛出未授权异常
 */
@Service
public class JwtService {
    // JWT签名密钥，由配置文件读取，用于加密与校验token
    private final SecretKey key;
    // token有效时长，单位：秒，读取配置文件
    private final long expirationSeconds;

    /**
     * 构造函数注入JWT配置
     * @param secret 配置文件中的密钥字符串，至少32位，满足HS256加密要求
     * @param expirationSeconds token有效期（秒）
     */
    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds
    ) {
        // 将字符串密钥转为加密所需的SecretKey对象
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * 根据用户信息生成JWT访问令牌
     * @param user 登录成功的用户实体
     * @return 已签名的JWT字符串
     */
    public String generate(SysUser user,String sessionId) {
        //JWT 标准里的过期时间 exp、签发时间 iat 规定使用 UTC 时间戳
        Instant now = Instant.now();
        return Jwts.builder()
                // subject：存放用户ID，作为唯一身份标识
                .subject(String.valueOf(user.getId()))
                //会话编号
                .claim("sid",sessionId)
                // 自定义载荷：角色信息，权限拦截时使用
                .claim("role", user.getRole())
                // 自定义载荷：部门ID，用于数据权限控制
                .claim("departmentId", user.getDepartmentId())
                // 设置令牌签发时间
                .issuedAt(Date.from(now))
                // 设置令牌过期时间
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                // 使用密钥进行签名，防止Token被篡改
                .signWith(key)
                // 拼接生成最终token字符串
                .compact();
    }

    /**
     * 解析Token，获取登录用户ID
     * 同时自动校验签名合法性、是否过期
     * @param token 请求头携带的JWT令牌
     * @return 登录用户userId
     */
    public JwtIdentity parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    // 使用密钥校验签名，如果token被篡改则直接抛出异常
                    .verifyWith(key)
                    .build()
                    // 解析并校验token
                    .parseSignedClaims(token)
                    .getPayload();
            String subject=claims.getSubject();
            //取下key为sid的值，并强制转为String
            String sessionId=claims.get("sid",String.class);
            if(subject==null||subject.isBlank()
            ||sessionId==null
            ||sessionId.isBlank())
                throw new IllegalArgumentException("Token缺少必要身份信息");
            return new JwtIdentity(Long.valueOf(subject),sessionId);
        } catch (Exception e) {
            // 捕获所有JWT相关异常：过期、签名错误、格式非法等，统一封装业务异常
            throw new BusinessException(401,"Token 无效或已过期,请重新登录");
        }
    }
}