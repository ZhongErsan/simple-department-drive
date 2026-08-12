package com.easypan.config;

import com.easypan.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局配置类
 * 实现两个核心功能：
 * 1. 注册登录认证拦截器，配置接口拦截与放行规则
 * 2. 全局跨域配置，解决前端浏览器跨域请求报错问题
 */
//标识这是一个配置类，Spring 项目启动时自动加载里面的配置。
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    // 登录认证拦截器
    private final AuthInterceptor authInterceptor;

    /**
     * 注册自定义拦截器
     * @param registry 拦截器注册器
     */
    //InterceptorRegister:  Spring MVC 提供的拦截器注册登记器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //把我们写好的 AuthInterceptor 交给 Spring 管理，登记注册这个拦截器。
        registry.addInterceptor(authInterceptor)
                // 拦截所有 /api/ 开头的接口
                .addPathPatterns("/api/**")
                // 无需登录放行的接口
                .excludePathPatterns("/api/auth/login", "/api/test");
        /*
        规则说明：
        1. /api/auth/login：登录接口，用户还没有token，必须放行
        2. /api/test：测试接口，无需身份认证
        其余所有/api下接口，都会经过AuthInterceptor执行登录校验
        */
    }

    /**
     * 全局跨域资源共享(CORS)配置
     * 前端Vue/React等项目访问后端接口属于跨域，浏览器会进行同源策略限制，该配置放开限制
     * @param registry 跨域配置注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")               // 对/api/下所有接口生效
                .allowedOriginPatterns("*")          // 允许所有来源域名访问（生产环境建议指定前端域名，不要*）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的请求方式
                .allowedHeaders("*");                 // 允许所有请求头（包含Authorization携带token）
    }
}