package com.easypan.controller;

import com.easypan.common.Result;
import com.easypan.model.vo.FileView;
import com.easypan.model.vo.PageResult;
import com.easypan.model.vo.TrashFileView;
import com.easypan.service.DriveFileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;


@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final DriveFileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileView> upload(
            @RequestParam Long folderId,
            @RequestPart("file") MultipartFile file
    ) {
        return Result.success("文件上传成功", fileService.upload(folderId, file));
    }

    @GetMapping
    public Result<PageResult<FileView>> list(
            @RequestParam Long folderId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量不能小于1")@Max(value = 100,message = "每页数量不能超过100") long pageSize) {
        return Result.success(fileService.list(folderId,pageNum,pageSize));
    }

    /**
     * 文件下载接口
     * @param id 文件数据库主键id
     * @return ResponseEntity封装文件资源流，浏览器触发下载
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        // 调用业务层：校验权限、查询文件元数据、封装磁盘文件资源DownloadFile
        DriveFileService.DownloadFile downloadFile = fileService.download(id);

        MediaType mediaType;
        try {
            // 判断数据库是否保存文件MIME类型
            // 有类型则正常解析；无类型使用通用二进制流（浏览器自动弹出下载）
            mediaType = downloadFile.file().getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(downloadFile.file().getContentType());
        } catch (Exception e) {
            // MIME类型解析异常兜底，默认二进制文件
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        // 构建响应附件头，attachment代表下载模式；设置原始文件名，指定UTF-8解决中文文件名乱码
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloadFile.file().getOriginalName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType) // 设置响应数据MIME类型
                .contentLength(downloadFile.file().getFileSize()) // 返回文件大小，浏览器显示进度条
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()) // 下载附件头
                .body(downloadFile.resource()); // 放入封装好的本地文件资源FileSystemResource
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success("文件已删除");
    }
    //回收站
    @GetMapping("/trash")
    public Result<List<TrashFileView>> trash(){
        return Result.success(fileService.listTrash());
    }
    //恢复
    @PutMapping("/{id}/restore")
    public Result<FileView> restore(@PathVariable Long id){
        return Result.success("文件恢复成功",fileService.restore(id));
    }
    //永久删除
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanentDelete(@PathVariable Long id){
        fileService.permanentDelete(id);
        return Result.success("文件已永久删除");
    }

}
