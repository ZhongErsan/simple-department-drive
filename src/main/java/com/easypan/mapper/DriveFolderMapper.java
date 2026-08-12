package com.easypan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easypan.model.entity.DriveFolder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface DriveFolderMapper extends BaseMapper<DriveFolder> {
    @Update("""
    UPDATE drive_folder
    SET department_id = #{newDepartmentId},
        updated_at = #{updatedAt}
    WHERE owner_id = #{ownerId}
      AND area_type = 'PERSONAL'
    """)
    int migratePersonalFolders(
            @Param("ownerId") Long ownerId,
            @Param("newDepartmentId") Long newDepartmentId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

}
