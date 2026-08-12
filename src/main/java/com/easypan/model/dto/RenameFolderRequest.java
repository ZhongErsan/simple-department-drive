package com.easypan.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFolderRequest(
        @NotBlank(message = "文件夹名不能为空")
        @Size(max=100,message = "文件夹名不能超过100个字符")
        String folderName
)  {
}
