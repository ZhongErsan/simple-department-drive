package com.easypan.model.vo;

import java.time.LocalDateTime;

public record FileView (
        Long id,
        Long folderId,
        String fileName,
        Long fileSize,
        String sha256,
        String contentType,
        Long uploaderId,
        String uploaderName,
        LocalDateTime createdAt
){
}
