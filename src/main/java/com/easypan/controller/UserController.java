package com.easypan.controller;

import com.easypan.common.Result;
import com.easypan.model.dto.CreateUserRequest;
import com.easypan.model.dto.ResetPasswordRequest;
import com.easypan.model.dto.UpdateUserRequest;
import com.easypan.model.vo.PageResult;
import com.easypan.model.vo.UserVO;
import com.easypan.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
        UserVO user = userService.create(request);
        return Result.success("用户创建成功", user);
    }

    @GetMapping
    public Result<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于1") @Max(value = 100, message = "每页数量不能超过100") long pageSize
    ) {
        return Result.success(userService.list(pageNum,pageSize));
    }

    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        UserVO user = userService.get(id);
        return Result.success(user);
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        UserVO user = userService.update(id, request);
        return Result.success("用户修改成功", user);
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return Result.success("密码重置成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return Result.success("用户已禁用");
    }
}
