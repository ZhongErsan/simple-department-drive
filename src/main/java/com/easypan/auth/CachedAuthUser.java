package com.easypan.auth;

import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.enums.Role;

public record CachedAuthUser(
        Long userId,
        String username,
        String realName,
        String role,
        Long departmentId,
        String status
) {
    public static CachedAuthUser from(SysUser user){
        return new CachedAuthUser(user.getId(),user.getUsername(),user.getRealName(),user.getRole(),user.getDepartmentId(),user.getStatus());
    }
    public boolean isActive(){
        return DataStatus.ACTIVE.name().equals(status);
    }
    public CurrentUser toCurrentUser(String sessionId){
        return new CurrentUser(userId,username,realName, Role.valueOf(role),departmentId,sessionId);
    }
}
