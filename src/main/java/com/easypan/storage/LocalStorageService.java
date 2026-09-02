package com.easypan.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

public interface LocalStorageService {
    StoredFile store(Long departmentId, MultipartFile file,String extension);
    Resource load(String storagePath);
    void delete(String storagePath);
    //查找指定时间之前写入的物理文件
    List<StorageObject> listFilesOlderThan(Instant cutoff,int limit);
}
