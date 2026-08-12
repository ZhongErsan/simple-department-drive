package com.easypan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drive_file")
public class DriveFile {
    @TableId(type= IdType.AUTO)
    private Long id;
    private Long departmentId;
    private Long folderId;
    private Long uploaderId;
    private Long ownerId;
    private String storageName;
    private String originalName;
    private String storagePath;
    private Long fileSize;
    /**
     * 文件原始字节内容的 SHA-256，小写十六进制，共 64 个字符。
     */
    private String sha256;
    private String contentType;
    private String status;
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
