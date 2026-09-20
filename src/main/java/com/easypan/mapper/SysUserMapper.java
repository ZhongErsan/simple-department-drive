package com.easypan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easypan.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    /**
     * 原子占用个人配额。
     * <p>
     * 只有剩余容量充足时才更新成功。
     */
    @Update("""
            UPDATE sys_user
            SET used_bytes = used_bytes + #{bytes},
                updated_at = NOW(6)
            WHERE id = #{userId}
              AND status = 'ACTIVE'
              AND used_bytes + #{bytes} <= quota_bytes
            """)
    int tryConsumeQuota(
            @Param("userId") Long userId,
            @Param("bytes") long bytes
    );

    //释放个人配额
    @Update("""
            UPDATE sys_user
            SET used_bytes=GREATEST(
                           used_bytes-#{bytes},
                           0
            ),
                updated_at=NOW(6)
            WHERE id=#{userId}
            """)
    int releaseQuota(@Param("userId") Long userId,
                     @Param("bytes") long bytes);
    //修改个人配额

    @Update("""
            UPDATE sys_user
            SET real_name = #{realName},
                role = #{role},
                department_id = #{departmentId},
                quota_bytes = #{quotaBytes},
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND COALESCE(used_bytes, 0) <= #{quotaBytes}
            """)
    int updateUserConditionally(
            @Param("id") Long id,
            @Param("realName") String realName,
            @Param("role") String role,
            @Param("departmentId") Long departmentId,
            @Param("quotaBytes") Long quotaBytes,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update("""
            UPDATE sys_user
            SET status='ACTIVE',
                updated_at=NOW(6)
            WHERE id=#{userId}
            """)
    int enabled(@Param("userId") Long userId);



    //重置密码
    @Update("""
            UPDATE sys_user
            SET password=#{password},
                updated_at=NOW(6)
            WHERE id=#{userId}
            """)
    int resetPassword(
            @Param("userId") Long userId,
            @Param("password") String password
    );

    //禁用用户
    @Update("""
            UPDATE sys_user
            SET status='DISABLED',
                updated_at=NOW(6)
            WHERE id=#{userId}
            """)
    int disabled(@Param("userId") Long userId);


}
