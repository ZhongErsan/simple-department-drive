package com.easypan.cache;

public final class CacheKeys {

    private static final String PREFIX =
            "simple-drive:cache:";

    private CacheKeys() {
    }

    /**
     * 用户详情
     *
     * simple-drive:cache:user-detail:1
     */
    public static String userDetail(Long userId) {
        return PREFIX + "user-detail:" + userId;
    }

    /**
     * 部门详情
     *
     * simple-drive:cache:department:1
     */
    public static String departmentDetail(
            Long departmentId
    ) {
        return PREFIX
                + "department:"
                + departmentId;
    }
    public static String authUser(Long userId,String sessionId){
        return PREFIX+"auth-user:"+userId+":"+sessionId;
    }
    /**
     * 部门列表
     */
    public static final String DEPARTMENT_LIST =
            PREFIX + "department:list";
}