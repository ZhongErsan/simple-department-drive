package com.easypan.model.vo;

public record LoginResponse (
        String token,
        Long userId,
        String realName,
        String role,
        Long departmentId
){

}
