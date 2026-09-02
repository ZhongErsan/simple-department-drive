package com.easypan.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStorageServiceImplSha256Test {

    /**
     * 字符串 hello 的标准 SHA-256。
     */
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29" +
                    "e1b161e5c1fa7425e73043362938b9824";


    /**
     * JUnit 为每个测试创建独立临时目录。
     * 测试结束后自动清理，不要使用 D:/temp_test_storage。
     */
    @TempDir
    Path tempDir;

    private LocalStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                tempDir.toString(),
                50L * 1024 * 1024
        );

        storageService =
                new LocalStorageServiceImpl(properties);

        // 测试中没有 Spring 容器，不会自动执行 @PostConstruct。
        storageService.init();
    }

    /**
     * 验证三个结果：
     *
     * 1. store 返回的 SHA-256 是正确的；
     * 2. 文件确实写入磁盘；
     * 3. 从磁盘重新读取计算出的 SHA 与 store 返回值一致。
     */
    @Test
    void shouldCalculateSha256FromActuallyStoredBytes()
            throws Exception {

        byte[] content =
                "hello".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                content
        );

        StoredFile storedFile =
                storageService.store(
                        2L,
                        file,
                        "txt"
                );

        Path storedPath = tempDir
                .resolve(storedFile.storagePath())
                .normalize();

        // 验证已知内容 hello 的标准 SHA-256。
        assertEquals(
                HELLO_SHA256,
                storedFile.sha256()
        );

        // 验证返回的文件大小是实际写入的字节数。
        assertEquals(
                content.length,
                storedFile.size()
        );

        // 验证文件真实存在。
        assertTrue(
                Files.isRegularFile(storedPath)
        );

        // 验证磁盘内容没有发生改变。
        assertArrayEquals(
                content,
                Files.readAllBytes(storedPath)
        );

        // 独立地从磁盘重新计算一次 SHA-256。
        assertEquals(
                calculateSha256(storedPath),
                storedFile.sha256()
        );
    }

    /**
     * 相同内容即使保存为不同的随机文件名，
     * SHA-256 也必须完全相同。
     */
    @Test
    void sameContentShouldProduceSameSha256ButDifferentStorageNames() {
        byte[] content =
                "same content".getBytes(StandardCharsets.UTF_8);

        StoredFile first = storageService.store(
                2L,
                new MockMultipartFile(
                        "file",
                        "a.txt",
                        "text/plain",
                        content
                ),
                "txt"
        );

        StoredFile second = storageService.store(
                2L,
                new MockMultipartFile(
                        "file",
                        "b.txt",
                        "text/plain",
                        content
                ),
                "txt"
        );

        // SHA 只由文件内容决定。
        assertEquals(
                first.sha256(),
                second.sha256()
        );

        // 物理存储名称使用 UUID，每次应当不同。
        assertNotEquals(
                first.storageName(),
                second.storageName()
        );

        assertNotEquals(
                first.storagePath(),
                second.storagePath()
        );
    }

    /**
     * 不同内容应当产生不同 SHA-256。
     */
    @Test
    void differentContentShouldProduceDifferentSha256() {
        StoredFile first = storageService.store(
                2L,
                new MockMultipartFile(
                        "file",
                        "first.txt",
                        "text/plain",
                        "first".getBytes(StandardCharsets.UTF_8)
                ),
                "txt"
        );

        StoredFile second = storageService.store(
                2L,
                new MockMultipartFile(
                        "file",
                        "second.txt",
                        "text/plain",
                        "second".getBytes(StandardCharsets.UTF_8)
                ),
                "txt"
        );

        assertNotEquals(
                first.sha256(),
                second.sha256()
        );
    }

    /**
     * 独立读取磁盘文件并重新计算 SHA-256。
     *
     * 这个方法不使用 LocalStorageServiceImpl 的计算结果，
     * 因此可以真正验证 store 内部的摘要计算是否正确。
     */
    private String calculateSha256(Path path)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] buffer = new byte[8192];

        try (InputStream input = Files.newInputStream(path)) {
            int length;

            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
        }

        return HexFormat
                .of()
                .formatHex(digest.digest());
    }
}
