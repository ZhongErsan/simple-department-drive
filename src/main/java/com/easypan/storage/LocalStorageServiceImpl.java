package com.easypan.storage;

import com.easypan.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class LocalStorageServiceImpl implements LocalStorageService {
    private final Path root;

    public LocalStorageServiceImpl(StorageProperties properties) {
        this.root = Path.of(properties.rootPath()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BusinessException(400, "无法创建存储根目录：" + root);
        }
    }

    @Override
    public StoredFile store(Long departmentId, MultipartFile file, String extension) {

        LocalDate today = LocalDate.now();
        String storageName = UUID.randomUUID().toString().replace("-", "")
                + (extension.isBlank() ? "" : "." + extension);
        String relative = "files/department-" + departmentId
                + "/" + today.getYear()
                + "/" + String.format("%02d", today.getMonthValue())
                + "/" + storageName;

        Path target = safeResolve(relative);
        try {
            // 创建目标文件所在的父文件夹（多级目录自动创建）
            Files.createDirectories(target.getParent());
            // 初始化SHA256摘要计算器
            MessageDigest messageDigest = createSha256Digest();
            // 记录最终写入磁盘的文件字节大小
            long storedSize;
            // try-with-resources：所有实现AutoCloseable的流，代码块结束自动关闭，避免句柄泄漏
            try (InputStream input = file.getInputStream();

                 // DigestOutputStream：包装输出流，写入数据时自动同步送入MessageDigest计算哈希
                 DigestOutputStream output =

                         // 缓冲输出流，减少磁盘IO次数，提升写入性能
                         new DigestOutputStream(
                                 new BufferedOutputStream(

                                         // 创建文件输出流
                                         Files.newOutputStream(
                                                 target,
                                                 StandardOpenOption.CREATE_NEW,// 只新建文件，若文件已存在直接抛异常（防止意外覆盖）
                                                 StandardOpenOption.WRITE
                                         )
                                 ),
                                 messageDigest
                         )) {

                // transferTo：将输入流全部数据转移到输出流
                // 数据一边写入磁盘，一边经过DigestOutputStream自动计算SHA256
                storedSize = input.transferTo(output);
            }

            // 将SHA256字节摘要转为小写十六进制字符串（固定64字符
            String sha256 = HexFormat.of().formatHex(messageDigest.digest());

            // 封装存储结果返回上层service
            return new StoredFile(storageName, relative, storedSize, sha256);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw new BusinessException(400, "文件保存失败");
        }
    }


    @Override
    public Resource load(String storagePath) {
        Path path = safeResolve(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(404, "磁盘文件不存在");
        }
        // FileSystemResource 用于封装本地磁盘文件路径，
        // 转为 Spring 的 Resource 资源对象，
        // 方便控制器直接返回实现文件下载，提供标准接口获取文件输入流。
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storagePath) {
        Path target = safeResolve(storagePath);
        try {
            Files.deleteIfExists(safeResolve(storagePath));
            pruneEmptyParents(target.getParent());
        } catch (IOException e) {
            throw new BusinessException("清理磁盘文件失败：" + e.getMessage());
        }

    }

    /**
     * 递归扫描存储目录，查询【修改时间早于指定时间点】的文件列表
     * 用于定时任务识别孤儿文件：只筛选老旧文件，避免扫描刚刚上传、事务尚未提交的新文件，防止误清理
     *
     * @param cutoff 时间阈值：只返回最后修改时间早于该时刻的物理文件
     * @param limit  单次最多返回文件数量，控制批量处理负载，避免一次性加载海量文件占用内存
     * @return 符合条件的物理文件封装集合StorageObject
     */
    @Override
    public List<StorageObject> listFilesOlderThan(Instant cutoff, int limit) {
        // 时间阈值不允许为空
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff不能为空");
        }

        // 限制条数小于等于0，无需扫描，直接返回空集合
        if (limit <= 0) {
            return List.of();
        }

        // 拼接文件存储根目录并标准化路径，消除路径跳转字符，防止路径穿越风险
        Path filesRoot = root.resolve("files").normalize();

        // 如果目标存储目录不存在/不是文件夹，没有任何文件，直接返回空
        if (!Files.isDirectory(filesRoot)) {
            return List.of();
        }

        // Files.walk：递归遍历目录下所有层级文件；try-with-resources自动关闭文件流，避免句柄泄漏
        try (Stream<Path> paths = Files.walk(filesRoot)) {
            return paths
                    // 过滤：只保留普通文件，排除文件夹、软链接
                    .filter(Files::isRegularFile)
                    // 将Path路径转换为业务载体StorageObject，封装路径、大小、修改时间
                    .map(this::toStorageObject)
                    // 核心过滤：筛选文件最后修改时间 < 临界时间（只拿老旧文件）
                    .filter(object ->
                            object.lastModifiedAt().isBefore(cutoff)
                    )
                    // 按照文件最后修改时间升序排序，优先处理最早的旧文件
                    .sorted(
                            Comparator.comparing(
                                    StorageObject::lastModifiedAt
                            )
                    )
                    // 截断流，最多读取limit条，控制内存占用
                    .limit(limit)
                    // 收集为不可变List返回
                    .toList();
        } catch (IOException e) {
            // IO异常：目录无权限访问、磁盘异常、目录被删除等场景抛出业务异常
            throw new BusinessException(
                    500,
                    "扫描存储目录失败：" + e.getMessage()
            );
        }

    }



    //创建摘要对象的方法
    private MessageDigest createSha256Digest() {
        // 获取SHA256算法实例
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("当前JAVA环境不支持SHA-256", e);
        }
    }

    private Path safeResolve(String storagePath) {
        Path resolved = root.resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(400, "非法存储路径");
        }
        return resolved;
    }

    /**
     * 将磁盘Path路径对象转换为存储对象StorageObject
     * 定时任务遍历文件目录时使用，封装文件相对路径、大小、最后修改时间
     *
     * @param path 磁盘文件绝对路径
     * @return 封装后的StorageObject载体
     * @throws BusinessException 文件属性读取发生IO异常时抛出
     */
    private StorageObject toStorageObject(Path path) {
        try {
            // 将绝对路径转为相对于存储根目录的相对路径
            // 统一分隔符为正斜杠，保证Windows/Linux系统路径格式一致
            String storagePath = root.relativize(path).toString().replace(File.separatorChar, '/');
            return new StorageObject(
                    storagePath,
                    Files.size(path),// 获取文件字节大小
                    Files.getLastModifiedTime(path).toInstant()// 获取文件最后修改时间
            );
        } catch (IOException e) {
            throw new BusinessException(500, "读取存储文件属性失败：" + e.getMessage());
        }

    }

    /**
     * 文件物理删除后的辅助方法：逐层向上清理空的年月分级目录
     * 上传文件采用 /files/2026/08/03 日期目录结构，删除文件后目录容易残留空文件夹
     * 安全限制：向上递归最多删除至 storage/files 一级，禁止删除files根目录，防止路径逻辑错误导致根目录丢失
     *
     * @param start 被删除文件所在的文件Path，从此文件的父目录开始向上检查空目录
     * @throws IOException 文件列表读取、目录删除出现IO异常（权限/文件被占用）
     */
    private void pruneEmptyParents(Path start) throws IOException {
        // 定义文件存储根目录边界
        Path filesRoot = root.resolve("files").normalize();
        // 从被删除文件所在层级开始向上遍历
        Path current = start.getParent();
        while (current != null && current.startsWith(filesRoot)
                && !current.equals(filesRoot)) {
            //读取 current 目录下所有直接子项
            try (Stream<Path> children = Files.list(current)) {
                //findAny()：只要找到任意一个子文件 / 子文件夹就立刻停止遍历（不用遍历全部，性能很好）
                //.isPresent()：判断是否找到了内容
                if (children.findAny().isPresent())
                    return;
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

}
