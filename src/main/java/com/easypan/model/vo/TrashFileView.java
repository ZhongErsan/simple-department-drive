package com.easypan.model.vo;

import java.time.LocalDateTime;

public record TrashFileView(
        Long id,
        Long folderId,
        String fileName,
        Long fileSize,
        String contentType,
        Long uploaderId,
        String uploaderName,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
}
