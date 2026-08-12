package com.easypan.auth;

import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.Role;

public record CurrentUser (
        Long userId,
        String username,
        String realName,
        Role role,
        Long departmentId,
        String sessionId
){
    public CurrentUser(
            Long userId,
            String username,
            String realName,
            Role role,
            Long departmentId
    ){
        this(userId,username,realName,role,departmentId,null);
    }
    public static CurrentUser from(SysUser user,String sessionId){
        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                Role.valueOf(user.getRole()),
                user.getDepartmentId(),
                sessionId
        );
    }
    public boolean isAdmin(){
        return role==Role.ADMIN;
    }

    public boolean isMinister(){
        return role==Role.MINISTER;
    }

    public boolean isMember(){
        return role==Role.MEMBER;
    }
}
