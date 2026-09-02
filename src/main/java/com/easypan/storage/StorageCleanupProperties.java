package com.easypan.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 存储孤儿文件清理任务配置参数
 * 配置前缀：storage.cleanup
 * 用于定时清理孤儿文件、清理任务重试策略控制
 * Record紧凑构造器实现参数合法性校验，项目启动加载配置时提前拦截非法配置
 */
@ConfigurationProperties(prefix = "storage.cleanup")
public record StorageCleanupProperties(
        /**
         * 是否开启孤儿文件自动清理功能
         * true：启动定时扫描与文件清理；false：关闭整套清理逻辑
         */
        @DefaultValue("true")
        boolean enabled,

        /**
         * 孤儿文件最小存活时间阈值
         * 文件磁盘修改时间必须早于【当前时间 - orphanMinAge】，才会被认定为待清理孤儿文件
         * 作用：设置安全窗口期，避免删除刚上传、数据库事务尚未提交完成的正常文件
         * 默认：1小时 PT1H
         */
        @DefaultValue("PT1H")
        Duration orphanMinAge,

        /**
         * 单次批量处理任务数量
         * 定时任务一轮最多取出多少条清理任务执行，限制IO并发，防止磁盘压力过高
         */
        @DefaultValue("100")
        int batchSize,

        /**
         * 清理任务首次失败基础重试间隔
         * 文件删除失败（文件占用、权限不足）时，下一次重试的基础等待时长
         * 一般配合指数退避策略使用
         * 默认：1分钟 PT1M
         */
        @DefaultValue("PT1M")
        Duration retryBaseDelay,

        /**
         * 清理任务最大重试间隔上限
         * 多次连续失败后，重试间隔不会无限拉长，最高不超过该值，避免任务永久搁置
         * 默认：1小时 PT1H
         */
        @DefaultValue("PT1H")
        Duration retryMaxDelay

) {
    /**
     * Record紧凑构造器（compact constructor）
     * Spring绑定yml配置完成后自动执行，对所有配置项进行合法性校验
     * 配置参数非法时，应用启动直接失败，提前暴露错误，避免运行时异常
     */
    public StorageCleanupProperties {
        if (orphanMinAge == null || orphanMinAge.isNegative()) {
            throw new IllegalArgumentException(
                    "storage.cleanup.orphan-min-age不能为负数"
            );
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "storage.cleanup.batch-size必须大于0"
            );
        }

        if (retryBaseDelay == null
                || retryBaseDelay.isZero()
                || retryBaseDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "storage.cleanup.retry-base-delay必须大于0"
            );
        }

        if (retryMaxDelay == null
                || retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            throw new IllegalArgumentException(
                    "retry-max-delay不能小于retry-base-delay"
            );
        }
    }
}
