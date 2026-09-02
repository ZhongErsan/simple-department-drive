package com.easypan.storage;
//产生时机：新文件上传保存完毕
//存储文件元数据载体
//LocalStorageService 保存文件后，将同时返回文件大小和 SHA-256。
public record StoredFile (
        String storageName,
        String storagePath,
        long size,
        String sha256
){
}
