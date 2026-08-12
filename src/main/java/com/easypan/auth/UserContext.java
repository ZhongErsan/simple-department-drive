package com.easypan.auth;


import com.easypan.exception.BusinessException;

public final class UserContext {
    private static final ThreadLocal<CurrentUser> CURRENT=new ThreadLocal<>();

    private UserContext(){

    }

    public static void set(CurrentUser user){
        CURRENT.set(user);
    }
    public static CurrentUser require(){
        CurrentUser user=CURRENT.get();
        if(user==null){
            throw new BusinessException(403,"请先登录");
        }
        return user;
    }

    public static void clear(){
        CURRENT.remove();
    }

}
