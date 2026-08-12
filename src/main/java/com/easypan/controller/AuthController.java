package com.easypan.controller;

import com.easypan.common.Result;
import com.easypan.model.dto.LoginRequest;
import com.easypan.model.vo.LoginResponse;
import com.easypan.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request){
        return Result.success("登录成功",authService.login(request));
    }
    @PostMapping("/logout")
    public Result<Void> logout(){
        authService.logout();
        return Result.success("退出登录成功");
    }
}
