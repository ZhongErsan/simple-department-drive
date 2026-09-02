package com.easypan.storage;

import java.time.Instant;
//产生时机：定时任务遍历磁盘
//磁盘中已经存在的物理文件
public record StorageObject (
        String storagePath,
        long size,
        Instant lastModifiedAt
){
}
