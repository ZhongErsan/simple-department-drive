package com.easypan.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        @DefaultValue("PT5M")
        Duration userDetailTtl,
        @DefaultValue("PT2M")
        Duration departmentTtl,
        @DefaultValue("PT2M")
        Duration authUserTtl
) {
}
