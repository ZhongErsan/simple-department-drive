package com.easypan.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "真实姓名不能为空")
        @Size(max=50,message = "真实姓名不能超过50个字符")
        String realname,
        Long departmentId,
        @NotBlank(message = "角色不能为空")
        String role,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotNull(message = "个人配额不能为空")
        @Positive(message = "个人配额必须大于0")
        Long quotaBytes

) {
}
