package com.easypan.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
//项目全局存储配置类
@ConfigurationProperties(prefix = "storage")
public record StorageProperties (
        String rootPath,
        long maxFileSize
){
}
