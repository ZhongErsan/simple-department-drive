package com.easypan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easypan.model.entity.StorageCleanupTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface StorageCleanupTaskMapper extends BaseMapper<StorageCleanupTask> {
    //同一个物理路径只保留一条待处理任务
    @Insert("""
            INSERT INTO storage_cleanup_task(
                                             storage_path,
                                             reason,
                                             status,
                                             attempts,
                                             next_retry_at,
                                             last_error,
                                             created_at,
                                             updated_at
            )VALUES(
                    #{storagePath},
                     #{reason},
                                    'PENDING',
                                    0,
                                    #{nextRetryAt},
                                    #{lastError},
                                    #{now},
                                    #{now}
            )
            ON DUPLICATE KEY UPDATE
                            reason = VALUES(reason),
                            status = 'PENDING',
                            next_retry_at =
                                LEAST(next_retry_at, VALUES(next_retry_at)),
                            last_error = VALUES(last_error),
                            updated_at = VALUES(updated_at)
            """)
    int upsertPending(
            @Param("storagePath") String storagePath,
            @Param("reason") String reason,
            @Param("lastError") String lastError,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("now") LocalDateTime now
    );
}
