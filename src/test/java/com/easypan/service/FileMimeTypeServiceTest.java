package com.easypan.service;

import com.easypan.exception.BusinessException;
import com.easypan.model.entity.SysUser;
import com.easypan.storage.LocalStorageService;
import com.easypan.storage.LocalStorageServiceImpl;
import com.easypan.storage.StorageProperties;
import com.easypan.storage.StoredFile;
import org.apache.tika.config.TikaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FileMimeTypeService 文件类型检测服务单元测试
 * 测试核心安全逻辑：基于Tika魔数校验，防止文件后缀篡改攻击
 * 使用Spring MockMultipartFile模拟前端上传文件，无需启动完整Spring上下文
 */
class FileMimeTypeServiceTest {

    /**
     * 手动构造测试实例
     * 直接传入Tika检测器，不依赖Spring容器注入，单元测试轻量化运行
     */
    private final FileMimeTypeService service =
            new FileMimeTypeService(
                    TikaConfig
                            .getDefaultConfig()
                            .getDetector()
            );

    /**
     * 测试场景：合法PDF文件，后缀与真实文件内容匹配，校验放行
     */
    @Test
    void shouldAcceptRealPdf() {
        // 构造标准PDF文件起始魔数 %PDF-1.7
        byte[] pdfContent =
                "%PDF-1.7\n1 0 obj\n"
                        .getBytes(StandardCharsets.US_ASCII);

        // 模拟上传文件：文件名report.pdf，文件内容为合法PDF二进制
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "report.pdf",
                        "application/octet-stream", // 此处Content-Type随便填，程序不会信任前端类型
                        pdfContent
                );

        // 执行检测校验，后缀传入pdf
        String mime =
                service.detectAndValidate(file, "pdf");

        // 断言：识别出真实MIME为application/pdf，校验通过
        assertEquals("application/pdf", mime);
    }

    /**
     * 测试场景：安全核心用例
     * PNG图片二进制（PNG魔数），但是修改后缀为pdf进行伪装上传
     * 预期：Tika识别真实类型，判定后缀与内容不符，抛出415拒绝上传
     */
    @Test
    void shouldRejectPngRenamedAsPdf() {
        // PNG标准文件魔数头部字节 89 50 4E 47
        byte[] pngHeader = {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };

        // 模拟攻击文件：文件名fake.pdf，前端主动伪造Content-Type:application/pdf
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "fake.pdf",
                        "application/pdf", // 前端请求头类型，可被攻击者随意伪造
                        pngHeader
                );

        // 断言：执行校验时一定会抛出BusinessException异常
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.detectAndValidate(
                                file,
                                "pdf"
                        )
                );

        // 断言异常码为415：文件内容与扩展名不一致，拒绝上传
        assertEquals(415, exception.getCode());
    }

}