package com.easypan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
//清理任务实体
@Data
@TableName("storage_cleanup_task")
public class StorageCleanupTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String storagePath;
    private String reason;

    private String status;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
