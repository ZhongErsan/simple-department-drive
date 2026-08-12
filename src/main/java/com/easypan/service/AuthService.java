package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.auth.CurrentUser;
import com.easypan.auth.JwtService;
import com.easypan.auth.UserContext;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.dto.LoginRequest;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.username().trim())
        );

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword()))
            throw new BusinessException(400, "用户名或密码错误");

        if (!DataStatus.ACTIVE.name().equals(user.getStatus()))
            throw new BusinessException(403, "用户已被禁用");
        //每次登录重新生成一个随机sessionId,
        //惠普一次登录会直接覆盖前一次登录
        String sessionId = UUID.randomUUID().toString();
        int affectedRows =userMapper.replaceCurrentSession(user.getId(),sessionId);
        //房子和密码校验完成以后，用户恰好被管理员禁用
        if(affectedRows!=1)
            throw new BusinessException(403,"用户状态已经发生变化，请重新登录");
        String token=jwtService.generate(user,sessionId);
        return new LoginResponse(
                token,
                user.getId(),
                user.getRealName(),
                user.getRole(),
                user.getDepartmentId()
        );

    }
    @Transactional
    public void logout(){
        CurrentUser currentUser= UserContext.require();
        userMapper.clearCurrentSessionIfMatch(currentUser.userId(), currentUser.sessionId());
    }
}
