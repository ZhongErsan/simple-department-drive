package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easypan.auth.CurrentUser;
import com.easypan.auth.UserContext;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.DriveFileMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.DriveFile;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.vo.FileView;
import com.easypan.model.vo.PageResult;
import com.easypan.model.vo.TrashFileView;
import com.easypan.storage.LocalStorageService;
import com.easypan.storage.StorageProperties;
import com.easypan.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.cglib.core.Local;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriveFileService {
    private final FileMimeTypeService fileMimeTypeService;
    private final DriveFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final DriveFolderService folderService;
    private final DrivePermissionService permissionService;
    private final LocalStorageService storageService;
    private final StorageProperties storageProperties;
    private final TransactionFileCleanupRegistrar transactionFileCleanupRegistrar;
    private final OrphanFileCleanupService orphanFileCleanupService;
    //配额
    private final QuotaService quotaService;
    private final UserService userService;

    @Transactional
    public FileView upload(Long folderId, MultipartFile file) {
        CurrentUser user = UserContext.require();
        DriveFolder folder = folderService.getActive(folderId);
        permissionService.checkCanUpload(user, folder);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        if (file.getSize() > storageProperties.maxFileSize()) {
            throw new BusinessException(400, "文件不能超过50MB");
        }
        String originalName = normalizeFileName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        //上传校验
        String detectedContentType = fileMimeTypeService.detectAndValidate(file, extension);
        Long duplicateCount = fileMapper.selectCount(
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getFolderId, folderId)
                        .eq(DriveFile::getOriginalName, originalName)
                        .eq(DriveFile::getStatus, DataStatus.ACTIVE.name())
        );
        if (duplicateCount > 0)
            throw new BusinessException(409, "当前文件夹已存在同名文件");
        StoredFile storedFile = storageService.store(
                folder.getDepartmentId(),
                file,
                extension
        );
        /*
         * 文件已经成功写入磁盘。
         * 此时立刻注册事务完成回调。
         *
         * 必须放在 quotaService.consume 和 fileMapper.insert 前面，
         * 否则这两个操作抛异常时还没有注册清理。
         */
        boolean cleanupCallbackRegistered= transactionFileCleanupRegistrar.registerRollbackCleanup(storedFile.storagePath());
        try {
            //占用配额空间
            //必须放进 try 内。否则配额不足时不会执行磁盘清理。
            quotaService.consume(folder, storedFile.size());
            LocalDateTime now = LocalDateTime.now();
            DriveFile entity = new DriveFile();
            entity.setDepartmentId(folder.getDepartmentId());
            entity.setFolderId(folder.getId());
            entity.setUploaderId(user.userId());
            entity.setOwnerId(folder.getOwnerId() == null ? user.userId() : folder.getOwnerId());
            entity.setStorageName(storedFile.storageName());
            entity.setOriginalName(originalName);
            entity.setStoragePath(storedFile.storagePath());
            entity.setFileSize(storedFile.size());
            entity.setSha256(storedFile.sha256());
            entity.setCreatedAt(now);
            entity.setStatus(DataStatus.ACTIVE.name());
            //数据库保存文件类型
            entity.setContentType(detectedContentType);
            entity.setUpdatedAt(now);

            if (fileMapper.insert(entity) != 1)
                throw new BusinessException(400, "文件信息写入数据库失败");
            return toView(entity);
        } catch (DuplicateKeyException e) {
            // 场景1：唯一索引冲突，同目录同名文件
            BusinessException conflict = new BusinessException(409, "当前文件夹已存在同名文件" + e);
            cleanupImmediatelyWhenNoTransaction(cleanupCallbackRegistered,storedFile,"UPLOAD_DUPLICATE_NAME");
            throw conflict;

        } catch (RuntimeException e) {
            // 场景2：除唯一冲突以外的所有异常（IO、SQL异常、权限异常等）
            cleanupImmediatelyWhenNoTransaction(cleanupCallbackRegistered,storedFile,"UPLOAD_METHOD_FAILURE");
            //这里用 addSuppressed，避免清理文件时发生的异常覆盖真正的数据库异常。
            throw e;
        }
    }

    public PageResult<FileView> list(Long folderId, long pageNum, long pageSize) {
        CurrentUser user = UserContext.require();
        DriveFolder folder = folderService.getActive(folderId);
        permissionService.checkCanViewFolder(user, folder);
        // 分页查询文件，结果存入MyBatis-Plus分页对象 Page<DriveFile>
        Page<DriveFile> filePage = fileMapper.selectPage(
                // 分页参数：第几页、每页条数
                new Page<>(pageNum, pageSize),
                // 条件构造器：组装where条件、排序
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getFolderId, folderId)
                        .eq(DriveFile::getStatus, DataStatus.ACTIVE.name())
                        .orderByDesc(DriveFile::getCreatedAt)
                        .orderByDesc(DriveFile::getId)
        );
        List<FileView> records = convertToViews(filePage.getRecords());
        return new PageResult<>(
                filePage.getCurrent(),
                filePage.getSize(),
                filePage.getTotal(),
                filePage.getPages(),
                records
        );
    }

    public DownloadFile download(Long fileId) {
        CurrentUser user = UserContext.require();
        DriveFile file = getActive(fileId);
        DriveFolder folder = folderService.getActive(file.getFolderId());
        permissionService.checkCanViewFolder(user, folder);
        return new DownloadFile(file, storageService.load(file.getStoragePath()));
    }

    @Transactional
    public void delete(Long fileId) {
        CurrentUser user = UserContext.require();
        DriveFile file = getActive(fileId);
        DriveFolder folder = folderService.getActive(file.getFolderId());
        permissionService.checkCanDeleteFile(user, folder, file);
        int affected = fileMapper.markDeletedIfActive(fileId, LocalDateTime.now(),LocalDateTime.now());
        if (affected != 1)
            throw new BusinessException(409, "文件已被删除，请勿重复操作");
        quotaService.release(folder, file.getFileSize());
    }

    //永久删除文件
    @Transactional
    public void permanentDelete(Long fileId){
        CurrentUser user=UserContext.require();
        DriveFile file=getDeleted(fileId);
        DriveFolder folder=folderService.getById(file.getFolderId());
        if(folder==null)
            throw new BusinessException(404,"文件所属目录不存在");
        permissionService.checkCanDeleteFile(user,folder,file);
        String storagePath=file.getStoragePath();
        //先删除数据库记录
        int affected=fileMapper.deleteById(fileId);
        if(affected!=1)
            throw new BusinessException(500,"永久删除文件失败");
        //数据库事务真正COMMIT后，再清理磁盘
        boolean registered= transactionFileCleanupRegistrar.registerAfterCommitCleanup(storagePath,"PERMANENT_DELETE");
        if(!registered)
            throw new BusinessException(500,"无法注册永久删除清理任务");
    }
    @Transactional
    public FileView restore(long fileId){
        CurrentUser user=UserContext.require();
        DriveFile file=getDeleted(fileId);
        //源文件夹必须还存在
        DriveFolder folder=folderService.getActive(file.getFolderId());
        //校验权限
        permissionService.checkCanDeleteFile(user,folder,file);
        //判断原位置是否已经出现同名文件
        Long duplicateCount=fileMapper.selectCount(
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getFolderId,file.getFolderId())
                        .eq(DriveFile::getOriginalName,file.getOriginalName())
                        .eq(DriveFile::getStatus,DataStatus.ACTIVE.name())
        );
        if(duplicateCount>0)
            throw new BusinessException(409,"源目录已经存在同名文件，无法恢复");
        //删除时释放过配额，所以恢复必须重新占用
        quotaService.consume(folder,file.getFileSize());
        //恢复状态
        file.setStatus(DataStatus.ACTIVE.name());
        file.setDeletedAt(null);
        file.setUpdatedAt(LocalDateTime.now());
        int affected=fileMapper.updateById(file);
        if(affected!=1)
            throw new BusinessException(500,"文件恢复失败");
        return toView(file);
    }
    //查询全部已删除文件
    public List<TrashFileView> listTrash(){
        CurrentUser user=UserContext.require();
        List<DriveFile> files=fileMapper.selectList(
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getStatus,DataStatus.DELETED.name())
                        .eq(DriveFile::getUploaderId,user.userId())
                        .orderByDesc(DriveFile::getDeletedAt)
                        .orderByDesc(DriveFile::getId)
        );
        return convertToTrashViews(files);
    }

    private boolean canManageDeletedFile(CurrentUser user, DriveFile file) {
        DriveFolder folder=folderService.getById(file.getFolderId());
        if(folder==null)
            return false;
        try{
            permissionService.checkCanDeleteFile(user,folder,file);
            return true;
        }catch (BusinessException e){
            return false;
        }
    }

    /**
     * 正常情况下，事务回滚回调负责删除物理文件。
     *
     * 这里只处理没有经过Spring事务代理、
     * 因而没有注册到事务同步的兜底场景。
     */
    private void cleanupImmediatelyWhenNoTransaction(
            boolean cleanupCallbackRegistered,
            StoredFile storedFile,
           String reason
    ) {
        if(!cleanupCallbackRegistered){
            //没有事务钩子自动清理；必须手动登记清理任务，预防产生孤儿文件。
            orphanFileCleanupService.cleanupNowOrEnqueue(storedFile.storagePath(),reason);
        }
    }

    private DriveFile getActive(Long id) {
        DriveFile file = fileMapper.selectOne(
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getId, id)
                        .eq(DriveFile::getStatus, DataStatus.ACTIVE.name())
        );
        if (file == null)
            throw new BusinessException(404, "文件不存在");
        return file;
    }
    //已删除的文件
    private DriveFile getDeleted(Long id){
        DriveFile file=fileMapper.selectOne(
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getId,id)
                        .eq(DriveFile::getStatus,DataStatus.DELETED.name())
        );
        if(file==null)
            throw new BusinessException(404,"回收站中不存在该文件");
        return file;
    }
    private String normalizeFileName(String value) {
        String name = StringUtils.cleanPath(value == null ? "" : value.trim());
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "原文件名不能为空");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new BusinessException(400, "文件名包含非法路径符号");
        }
        if (name.length() > 255) {
            throw new BusinessException(400, "文件名不能超过255个字符");
        }
        return name;
    }

    private String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1)
            throw new BusinessException(400, "文件必须包含有效扩展名");
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private FileView toView(DriveFile file) {
        SysUser uploader = userMapper.selectById(file.getUploaderId());
        return new FileView(
                file.getId(),
                file.getFolderId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getSha256(),
                file.getContentType(),
                file.getUploaderId(),
                uploader == null ? "未知用户" : uploader.getRealName(),
                file.getCreatedAt()
        );
    }

    /**
     * 将数据库文件实体集合 转换为前端展示VO集合（FileView）
     * 采用【批量IN查询】方案，**消除经典N+1查询性能问题**
     * 场景：分页查询文件列表时，需要同时展示上传人名称
     * 原始危险写法：循环每个DriveFile，单独根据uploaderId查询用户（N+1）
     * 优化思路：先收集所有上传人ID，一次性批量查询全部用户，内存组装数据
     *
     * @param files 数据库查询得到的DriveFile文件实体列表
     * @return 可供前端直接返回的FileView视图对象集合
     */
    private List<FileView> convertToViews(
            List<DriveFile> files
    ) {
        // 边界判断：文件列表为空，直接返回空集合，避免后续无用查询
        if (files.isEmpty()) {
            return List.of();
        }

        // 1. 提取所有文件的上传者ID，放入Set自动去重，避免重复查询同一个用户
        Set<Long> uploaderIds = files.stream()
                .map(DriveFile::getUploaderId)
                .collect(Collectors.toSet());

        // 2. 根据上传人ID集合批量一次性查询用户信息（仅执行1次SQL）
        // 组装 Map<用户ID, 用户实体>，方便后续快速根据ID查找用户
        Map<Long, SysUser> uploaderMap =
                //SELECT * FROM sys_user WHERE id IN (1,3,5,7)
                userMapper.selectBatchIds(uploaderIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SysUser::getId,
                                Function.identity()
                        ));

        // 3. 遍历文件实体，从内存Map获取上传人信息，组装VO
        return files.stream()
                .map(file -> {
                    // 根据上传者id从内存map查找用户信息，无数据库访问
                    SysUser uploader = uploaderMap.get(file.getUploaderId());

                    // 三元表达式处理兜底：用户已删除则显示【未知用户】
                    String uploaderName =
                            uploader == null
                                    ? "未知用户"
                                    : uploader.getRealName();

                    // 实体转换为视图对象，注入上传者名称
                    return toView(file, uploaderName);
                })
                .toList();
    }
    //同样解决N+1查询
    private List<TrashFileView> convertToTrashViews(
            List<DriveFile> files
    ) {
        // 边界判断：文件列表为空，直接返回空集合，避免后续无用查询
        if (files.isEmpty()) {
            return List.of();
        }

        // 1. 提取所有文件的上传者ID，放入Set自动去重，避免重复查询同一个用户
        Set<Long> uploaderIds = files.stream()
                .map(DriveFile::getUploaderId)
                .collect(Collectors.toSet());

        // 2. 根据上传人ID集合批量一次性查询用户信息（仅执行1次SQL）
        // 组装 Map<用户ID, 用户实体>，方便后续快速根据ID查找用户
        Map<Long, SysUser> uploaderMap =
                //SELECT * FROM sys_user WHERE id IN (1,3,5,7)
                userMapper.selectBatchIds(uploaderIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SysUser::getId,
                                Function.identity()
                        ));

        // 3. 遍历文件实体，从内存Map获取上传人信息，组装VO
        return files.stream()
                .map(file -> {
                    // 根据上传者id从内存map查找用户信息，无数据库访问
                    SysUser uploader = uploaderMap.get(file.getUploaderId());

                    // 三元表达式处理兜底：用户已删除则显示【未知用户】
                    String uploaderName =
                            uploader == null
                                    ? "未知用户"
                                    : uploader.getRealName();

                    // 实体转换为视图对象，注入上传者名称
                    return toTrashView(file, uploaderName);
                })
                .toList();
    }
    private FileView toView(
            DriveFile file,
            String uploaderName
    ) {
        return new FileView(
                file.getId(),
                file.getFolderId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getSha256(),
                file.getContentType(),
                file.getUploaderId(),
                uploaderName,
                file.getCreatedAt()
        );
    }
    //返回文件夹回收站VO
    private TrashFileView toTrashView(DriveFile file,String uploaderName) {


        return new TrashFileView(
                file.getId(),
                file.getFolderId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getContentType(),
                file.getUploaderId(),
                uploaderName,
                file.getCreatedAt(),
                file.getDeletedAt()
        );
    }

    public record DownloadFile(
            DriveFile file,
            Resource resource
    ) {
    }
}
