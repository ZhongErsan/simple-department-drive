package com.easypan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easypan.model.entity.SysDepartment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {

    //原子占用部门配额
    @Update("""
            UPDATE sys_department
            SET used_bytes=used_bytes+#{bytes},
                updated_at=NOW(6)
            WHERE id=#{departmentId}
            AND status ='ACTIVE'
            AND used_bytes+#{bytes}<=quota_bytes
            """)
    int tryConsumeQuota(@Param("departmentId") Long departmentId,
                       @Param("bytes") long bytes);
    //释放部门配额
    @Update("""
            UPDATE sys_department
            SET used_bytes=GREATEST(
                           used_bytes-#{bytes},
                           0
            ),
                updated_at=NOW(6)
            WHERE id=#{departmentId}
            """)
    int releaseQuota(@Param("departmentId") Long departmentId,
                     @Param("bytes") long bytes);
    //原子更新
    @Update("""
            UPDATE sys_department
            SET department_name = #{departmentName},
                quota_bytes = #{quotaBytes},
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND COALESCE(used_bytes, 0) <= #{quotaBytes}
            """)
    int updateDepartmentConditionally(
            @Param("id") Long id,
            @Param("departmentName") String departmentName,
            @Param("quotaBytes") Long quotaBytes,
            @Param("updatedAt") LocalDateTime updatedAt
    );

}
