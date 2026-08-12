package com.easypan.model.vo;

import java.time.LocalDateTime;

public record UserVO (
        Long id,
        String username,
        String realName,
        String role,
        Long departmentId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long quotaBytes,
        Long usedBytes,
        Long remainingBytes
){
}
