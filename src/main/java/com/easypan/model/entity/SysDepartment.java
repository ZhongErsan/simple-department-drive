package com.easypan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_department")
public class SysDepartment {
    @TableId(type= IdType.AUTO)
    private Long id;
    private String departmentName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    //部门公共区和投稿区总配额，单位：字节
    private Long quotaBytes;
    //部门公共区和投稿区已使用容量，单位：字节
    private Long usedBytes;
}
