package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.auth.*;
import com.easypan.cache.CacheKeys;
import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
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
    private final LoginSessionService loginSessionService;
    private final RedisDataCacheService cacheService;
    private final CacheProperties cacheProperties;
    private final LoginProtectionService loginProtectionService;

    public LoginResponse login(LoginRequest request) {
        String username=request.username().trim();
        //1. 先检查账号是否已经锁定
        loginProtectionService.checkPasswordLock(username);
        //2. 查询用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.username().trim())
        );
        //3. 校验密码
        boolean passwordCorrect=user!=null&&passwordEncoder.matches(request.password(),user.getPassword());
        //密码错误：redis连续失败次数+1
        if(!passwordCorrect){
            loginProtectionService.recordPasswordFailure(username);
            throw new BusinessException(400,"用户名或密码错误");
        }
        //密码正确,连续失败次数立即清0
        loginProtectionService.clearPasswordFailures(username);
        //检查用户状态
        if (!DataStatus.ACTIVE.name().equals(user.getStatus()))
            throw new BusinessException(403, "用户已被禁用");
        //每次登录重新生成一个随机sessionId,
        //惠普一次登录会直接覆盖前一次登录
        String sessionId = UUID.randomUUID().toString();

        loginSessionService.replaceSession(user.getId(),sessionId);
        //登录成功时直接预热AUTH缓存
        cacheService.set(CacheKeys.authUser(user.getId(),sessionId),CachedAuthUser.from(user),cacheProperties.authUserTtl());

        String token=jwtService.generate(user,sessionId);
        return new LoginResponse(
                token,
                user.getId(),
                user.getRealName(),
                user.getRole(),
                user.getDepartmentId()
        );

    }

    public void logout(){
        CurrentUser currentUser= UserContext.require();
        // Redis层面：调用会话服务，执行Lua脚本，原子校验sessionId并删除Redis内的会话缓存
        // 只有Redis里保存的sessionId和当前用户携带sessionId一致，才会删除这条会话记录
        loginSessionService.removeSessionIfMatch(currentUser.userId(),currentUser.sessionId());
    }
}
