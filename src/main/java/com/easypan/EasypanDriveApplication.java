package com.easypan;

import com.easypan.storage.StorageCleanupProperties;
import com.easypan.storage.StorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.easypan")
@MapperScan("com.easypan.mapper")
//开启 Spring 内置定时任务调度功能
@EnableScheduling
@EnableConfigurationProperties({StorageProperties.class, StorageCleanupProperties.class})
public class EasypanDriveApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasypanDriveApplication.class, args);
    }
}

