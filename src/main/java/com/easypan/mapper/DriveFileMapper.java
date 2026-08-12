package com.easypan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easypan.model.entity.DriveFile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DriveFileMapper extends BaseMapper<DriveFile> {
    @Update("""
        UPDATE drive_file
        SET status = 'DELETED',
            deleted_at = #{deletedAt},
            updated_at = #{updatedAt}
        WHERE id = #{fileId}
          AND status = 'ACTIVE'
        """)
    int markDeletedIfActive(
            @Param("fileId") Long fileId,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update("""
    UPDATE drive_file f
    INNER JOIN drive_folder d
        ON d.id = f.folder_id
    SET f.department_id = #{newDepartmentId},
        f.updated_at = #{updatedAt}
    WHERE d.owner_id = #{ownerId}
      AND d.area_type = 'PERSONAL'
    """)
    int migratePersonalFiles(
            @Param("ownerId") Long ownerId,
            @Param("newDepartmentId") Long newDepartmentId,
            @Param("updatedAt") LocalDateTime updatedAt
    );
    /**
     * 查询仍被数据库引用的物理路径。
     *
     * 不限制 status。
     * 因为当前 DELETED 只是逻辑删除，物理文件仍然需要保留。
     */
    @Select("""
        <script>
        SELECT storage_path
        FROM drive_file
        WHERE storage_path IN
        <foreach
            collection="paths"
            item="path"
            open="("
            separator=","
            close=")">
            #{path}
        </foreach>
        </script>
        """)
    List<String> selectExistingStoragePaths(
            @Param("paths") Collection<String> paths
    );


}
