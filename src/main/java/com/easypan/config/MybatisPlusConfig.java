package com.easypan.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * 用于注册MyBatis-Plus内置插件，当前主要开启分页插件
 * 注意：如果不配置分页插件，Mybatis-Plus的Page对象分页查询不会生效，只会查询全部数据
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册MyBatisPlus拦截器核心Bean
     * 拦截器用于统一装载各类内置插件（分页、乐观锁、防全表更新插件等）
     * @return MybatisPlusInterceptor 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建MyBatisPlus拦截器容器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 创建分页内部拦截器，指定数据库类型为MySQL（自动适配分页SQL语法）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);

        // 设置分页溢出处理：false = 请求页码超过最大页数时，返回空数据，不自动跳转到第一页
        // true 则超出页码自动查询第1页，根据业务需求选择
        pagination.setOverflow(false);

        // 限制单页最大查询条数：客户端即使传入大于100的size，最多只返回100条数据
        // 防止恶意传入超大分页条数，造成数据库查询压力过大
        pagination.setMaxLimit(100L);

        // 将分页插件添加到拦截器链中
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}