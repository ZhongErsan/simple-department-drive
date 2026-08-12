package com.easypan.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateFolderRequest(
        @NotNull(message = "父文件夹ID不能为空")
        @Positive(message = "父文件夹ID必须大于0")
        Long parentId,
        @NotBlank(message = "文件夹名不能为空")
        @Size(max=100,message="文件夹名不能超过100个字符")
        String folderName
) {
}
