package com.easypan.service;

import com.easypan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileMimeTypeService {
    /**
     * 允许上传的文件配置：key=文件小写扩展名，value=该扩展名对应的合法真实MIME集合
     * 校验逻辑：用户上传后缀必须在key列表，并且文件真实类型必须匹配对应集合内的MIME
     *
     * 安全说明：
     * 1. 不依赖MultipartFile.getContentType()，该值由前端请求头传递，可随意伪造；
     * 2. 不只依靠扩展名校验，仅校验后缀极易被改名绕过；
     * 3. 双重校验：后缀白名单 + 文件二进制真实类型匹配。
     */
    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES =
            Map.ofEntries(
                    Map.entry("jpg", Set.of("image/jpeg")),
                    Map.entry("jpeg", Set.of("image/jpeg")),
                    Map.entry(
                            "png",
                            Set.of("image/png")
                    ),
                    Map.entry(
                            "gif",
                            Set.of("image/gif")
                    ),
                    Map.entry(
                            "pdf",
                            Set.of("application/pdf")
                    ),
                    Map.entry(
                            "doc",
                            Set.of("application/msword")
                    ),
                    Map.entry(
                            "docx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument." +
                                            "wordprocessingml.document"
                            )
                    ),
                    Map.entry(
                            "xls",
                            Set.of("application/vnd.ms-excel")
                    ),
                    Map.entry(
                            "xlsx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument." +
                                            "spreadsheetml.sheet"
                            )
                    ),
                    Map.entry(
                            "ppt",
                            Set.of("application/vnd.ms-powerpoint")
                    ),
                    Map.entry(
                            "pptx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument." +
                                            "presentationml.presentation"
                            )
                    ),
                    Map.entry(
                            "txt",
                            Set.of("text/plain")
                    ),
                    Map.entry(
                            "md",
                            Set.of(
                                    "text/plain",
                                    "text/markdown",
                                    "text/x-markdown"
                            )
                    ),
                    Map.entry(
                            "zip",
                            Set.of(
                                    "application/zip",
                                    "application/x-zip-compressed"
                            )
                    )
            );
    private final Detector tikaDetector;
    /**
     * 执行文件类型检测 + 合法性校验
     * @param file 前端上传的文件
     * @param extension 文件原始名称解析出来的小写后缀（不带.）
     * @return 校验通过后返回文件真实MIME类型，用于存入数据库DriveFile
     * @throws BusinessException 415不支持的媒体类型 / 400检测异常
     */
    public String detectAndValidate(MultipartFile file, String extension) {
        Set<String> allwedMimeTypes = ALLOWED_MIME_TYPES.get(extension);
        if (allwedMimeTypes == null)
            throw new BusinessException(415, "不允许上传该文件类型");

        // 使用Tika读取二进制，获取文件真实MIME
        String detectedMimeType = detect(file);
         /*
         application/octet-stream 是通用二进制流
         Tika无法识别文件魔数时返回该值，无法判定真实格式，出于安全拦截
        */
        if("application/octet-stream".equals(detectedMimeType)){
            throw new BusinessException(415,"无法识别文件真实类型");
        }
        // 关键校验：文件真实类型 和 后缀预期类型不匹配 → 判定为伪装文件，拒绝上传
        if(!allwedMimeTypes.contains(detectedMimeType))
            throw new BusinessException(415,"文件内容与扩展名不一致，检测类型为："+detectedMimeType);
        return detectedMimeType;
    }
    /**
     * 底层调用Tika检测器读取文件二进制，识别真实MIME类型
     * @param file 上传文件
     * @return 识别出的MIME类型字符串
     */
    private String detect(MultipartFile file) {
        //存放文件名、文件属性等描述信息的容器，辅助识别
        Metadata metadata = new Metadata();
        try (
                TikaInputStream inputStream = TikaInputStream.get(file.getInputStream())
        ){
            return tikaDetector.detect(inputStream,metadata).toString();
        }catch(IOException e){
            throw new BusinessException(400,"文件类型检测失败"+e);
        }
    }
}
