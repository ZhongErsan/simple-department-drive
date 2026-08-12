package com.easypan.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max=50,message="用户名不能超过50个字符")
        String username,
        @NotBlank(message ="密码不能为空")
        @Size(min=6,max=30,message="密码长度为6到30位")
        String password,
        @NotBlank(message = "真实姓名不能为空")
        @Size(max=50,message = "真实姓名不能超过50个字符")
        String realName,
        @NotBlank(message = "角色不能为空")
        String role,
        Long departmentId,
        @NotNull(message = "个人配额不能为空")
        @Positive(message = "个人配额必须大于0")
                Long quotaBytes
) {
}
