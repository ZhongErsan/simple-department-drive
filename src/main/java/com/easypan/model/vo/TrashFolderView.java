package com.easypan.model.vo;

import java.time.LocalDateTime;

public record TrashFolderView(
        Long id,
        Long parentId,
        Long departmentId,
        String folderName,
        String areaType,
        Long ownerId,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
}
