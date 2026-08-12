package com.easypan.config;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.apache.tika.detect.Detector;
import org.apache.tika.config.TikaConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Apache Tika 文件类型检测器配置类
 * 作用：将Tika检测器交由Spring容器管理为单例Bean，全局复用，避免频繁创建实例消耗资源
 * 核心用途：基于文件二进制魔数(Magic Number)识别文件真实MIME类型，
 * 不受文件名后缀、前端请求头Content-Type伪造的影响，用于上传安全校验
 */
@Configuration
public class TikaConfiguration {

    /**
     * 获取Tika内置检测器，注册为Spring Bean
     * Tika Detector 内置海量文件格式魔数库，读取文件头部二进制字节判定真实文件类型
     * 推荐注入Detector而非直接new Tika，粒度更轻，仅保留文件识别能力，减少不必要依赖加载
     * @return Tika文件检测器单例
     */
    @Bean
    public Detector tikaDetector(){
        // 加载Tika默认配置，获取标准文件格式检测器
        return TikaConfig.getDefaultConfig().getDetector();
    }
}
