package com.easypan.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest (
        @NotBlank(message = "新密码不能为空 ")
        @Size(min=6,max=30,message = "密码长度应为6到30位")
        String newPassword
){

}
