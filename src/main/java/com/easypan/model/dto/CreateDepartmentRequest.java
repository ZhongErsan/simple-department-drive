package com.easypan.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 100, message = "部门名称不能超过100个字符")
        String departmentName,
        @NotNull(message = "部门配额不能为空")
        @Positive(message = "部门配额必须大于0")
        Long quotaBytes
) {
}
