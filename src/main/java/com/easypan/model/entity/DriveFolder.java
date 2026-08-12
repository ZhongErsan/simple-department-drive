package com.easypan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drive_folder")
public class DriveFolder {
    @TableId(type= IdType.AUTO)
    private Long id;
    private Long departmentId;
    private Long parentId;
    private String folderName;
    private String areaType;
    private Long ownerId;
    private Long createdBy;
    private String status;
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
