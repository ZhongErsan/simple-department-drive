package com.easypan.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type= IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String role;
    private Long departmentId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    //个人空间总配额，单位：字节
    private Long quotaBytes;
    //个人空间已使用容量，单位：字节
    /** 用户已占用存储空间（字节），禁止普通updateById修改，仅允许原子SQL增减 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long usedBytes;

}
