package com.easypan.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;

/**
 * Redis通用缓存服务
 * 封装Redis读写删逻辑，统一序列化/反序列化（使用Jackson把对象转JSON字符串存入Redis）
 * 设计思想：缓存属于旁路增强，Redis读写异常**不阻断主业务**，只打warn日志，返回null，业务继续走数据库查询
 */
@Slf4j // lombok注解，自动注入log日志对象
@Service // 注册为Spring Bean，业务类可以直接注入使用
@RequiredArgsConstructor // lombok：构造注入，自动生成带final字段的构造函数
public class RedisDataCacheService {
    // Spring封装的Redis客户端，StringRedisTemplate读写全部是字符串
    private final StringRedisTemplate redisTemplate;
    // Jackson序列化工具：Java对象 ↔ JSON字符串
    private final ObjectMapper objectMapper;

    /**
     * 读取缓存【普通Java对象】
     * @param key Redis的key
     * @param type 目标实体Class，例如 SysUser.class
     * @return 缓存中的对象；key不存在 / Redis异常 / 解析失败，返回null
     */
    public <T> T get(
            String key,
            Class<T> type
    ) {
        try {
            // 根据key从Redis读取字符串（我们存的是JSON文本）
            String json =
                    redisTemplate.opsForValue()
                            .get(key);
            // 空判断：key不存在，直接返回null
            if (json == null || json.isBlank()) {
                return null;
            }
            // Jackson将JSON字符串反序列化为Java对象
            return objectMapper.readValue(
                    json,
                    type
            );
        } catch (
            // DataAccessException：Redis网络异常、连接失败等Redis相关异常
            // JsonProcessingException：JSON格式损坏，反序列化失败
                DataAccessException
                | JsonProcessingException e
        ) {
            /*
             * 核心设计：缓存只是优化手段，不是主数据源
             * Redis读取失败，不能抛异常打断业务流程
             * 返回null，上层业务会降级去查询MySQL数据库
             */
            log.warn(
                    "读取Redis缓存失败，key={}",
                    key,
                    e
            );
            return null;
        }
    }

    /**
     * 读取缓存【泛型集合对象】
     * 解决上面普通get无法处理泛型擦除的问题
     * 示例场景：List<SysDepartment>、Page<SysUser> 这种带泛型的类型
     * @param key Redis的key
     * @param typeReference TypeReference用来保留泛型信息，例如 new TypeReference<List<SysDepartment>>(){}
     * @return 泛型对象；失败返回null
     */
    public <T> T get(
            String key,
            TypeReference<T> typeReference
    ) {
        try {
            String json =
                    redisTemplate.opsForValue()
                            .get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            // 使用TypeReference反序列化，保留泛型
            return objectMapper.readValue(
                    json,
                    typeReference
            );
        } catch (
                DataAccessException
                | JsonProcessingException e
        ) {
            log.warn(
                    "读取Redis缓存失败，key={}",
                    key,
                    e
            );
            return null;
        }
    }

    /**
     * 写入缓存
     * @param key redis键
     * @param value 要存入的Java对象（会自动序列化为JSON）
     * @param ttl 过期时间，Duration类型，PT5M代表5分钟
     */
    public void set(
            String key,
            Object value,
            Duration ttl
    ) {
        try {
            // 将Java对象序列化为JSON字符串
            String json =
                    objectMapper.writeValueAsString(
                            value
                    );
            // 存入Redis并设置过期时间
            redisTemplate.opsForValue().set(
                    key,
                    json,
                    ttl
            );
        } catch (
                DataAccessException
                | JsonProcessingException e
        ) {
            /*
             * 写缓存失败不影响主业务
             * 数据库已经成功写入数据，缓存写入失败只打警告日志，不抛出异常
             * 后续请求只是不命中缓存，直接查数据库
             */
            log.warn(
                    "写入Redis缓存失败，key={}",
                    key,
                    e
            );
        }
    }

    /**
     * 删除缓存，支持一次性删除多个key
     * @param keys 可变参数，可以传单个key，也可以多个key
     */
    public void delete(
            String... keys
    ) {
        // 入参为空，直接返回，避免空指针
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            // 把字符串数组转为List，批量删除redis key
            redisTemplate.delete(
                    Arrays.asList(keys)
            );
        } catch (DataAccessException e) {
            log.warn(
                    "删除Redis缓存失败，keys={}",
                    Arrays.toString(keys),
                    e
            );
        }
    }
}
