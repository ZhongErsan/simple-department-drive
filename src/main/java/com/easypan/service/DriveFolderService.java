package com.easypan.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.auth.CurrentUser;
import com.easypan.auth.UserContext;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.DriveFileMapper;
import com.easypan.mapper.DriveFolderMapper;
import com.easypan.model.dto.CreateFolderRequest;
import com.easypan.model.dto.RenameFolderRequest;
import com.easypan.model.entity.DriveFile;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.vo.FolderView;
import com.easypan.model.vo.TrashFolderView;
import lombok.RequiredArgsConstructor;
import org.apache.xmlbeans.impl.store.Cur;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件夹业务服务
 * 负责网盘文件夹的查询、创建、重命名、删除等业务逻辑
 * 内置权限校验、同级文件夹名校验、名称合法性校验、文件夹非空校验
 * 采用逻辑删除方案，不会物理删除数据库记录
 */
@Service
@RequiredArgsConstructor
public class DriveFolderService {
    private final DriveFolderMapper folderMapper;
    private final DriveFileMapper fileMapper;
    private final DrivePermissionService permissionService;

    /**
     * 查询指定父目录下所有子文件夹列表
     *
     * @param parentId 父文件夹ID，0代表根目录
     * @return 子文件夹视图VO集合
     */
    public List<FolderView> listChildren(Long parentId) {
        // 获取当前登录用户信息
        CurrentUser user = UserContext.require();

        // 如果不是根目录，校验父文件夹查看权限
        if (parentId != 0L) {
            DriveFolder parent = getActive(parentId);
            permissionService.checkCanViewFolder(user, parent);
        }

        // 查询父目录下有效的文件夹，再过滤出当前用户有权查看的文件夹，转换为VO返回
        return folderMapper.selectList(
                        new LambdaQueryWrapper<DriveFolder>()
                                .eq(DriveFolder::getParentId, parentId)
                                .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name())
                                .orderByAsc(DriveFolder::getId)
                ).stream()
                // 过滤：只保留当前用户具备查看权限的文件夹
                .filter(folder -> permissionService.canViewFolder(user, folder))
                .map(this::toView)
                .toList();
    }

    /**
     * 创建文件夹
     *
     * @param request 创建文件夹请求DTO
     * @return 新建文件夹视图VO
     */
    @Transactional
    public FolderView create(CreateFolderRequest request) {
        CurrentUser user = UserContext.require();
        // 查询父文件夹，校验文件夹存在且未被删除
        DriveFolder parent = getActive(request.parentId());
        // 校验用户是否拥有在该父目录创建文件夹的权限
        permissionService.checkCanCreateFolder(user, parent);

        // 规范化文件夹名称，去除首尾空格，并校验名称合法性
        String folderName = normalizeName(request.folderName());
        // 校验同级目录不能存在同名文件夹
        ensureNoDuplicate(parent.getId(), folderName, null);

        LocalDateTime now = LocalDateTime.now();
        DriveFolder folder = new DriveFolder();
        // 子文件夹继承父文件夹的部门、区域类型、所有者信息
        folder.setDepartmentId(parent.getDepartmentId());
        folder.setParentId(parent.getId());
        folder.setFolderName(folderName);
        folder.setAreaType(parent.getAreaType());
        folder.setOwnerId(parent.getOwnerId());
        folder.setCreatedBy(user.userId());
        folder.setStatus(DataStatus.ACTIVE.name());
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        try {
            folderMapper.insert(folder);
        } catch (DuplicateKeyException e) {
            //不需要捕获所有异常，直接向上抛出
            throw new BusinessException(409, "同级目录下已存在同名文件夹" + e);
        }
        return toView(folder);
    }

    /**
     * 文件夹重命名
     *
     * @param id      目标文件夹ID
     * @param request 重命名请求DTO
     * @return 修改后的文件夹视图VO
     */
    @Transactional
    public FolderView rename(Long id, RenameFolderRequest request) {
        CurrentUser user = UserContext.require();
        DriveFolder folder = getActive(id);
        // 根目录禁止重命名
        if (folder.getParentId() == 0L) {
            throw new BusinessException(403, "系统根文件夹不能改名");
        }
        // 校验用户操作权限
        permissionService.checkCanCreateFolder(user, folder);

        String folderName = normalizeName(request.folderName());
        // 前置查重：排除自身ID，防止和同级其他文件夹重名
        ensureNoDuplicate(folder.getParentId(), folderName, id);
        folder.setFolderName(folderName);
        folder.setUpdatedAt(LocalDateTime.now());
        //重命名时捕获唯一键异常
        try {
            folderMapper.updateById(folder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    409,
                    "同级目录下已存在同名文件夹" + e
            );
        }
        return toView(folder);
    }

    /**
     * 删除文件夹（逻辑删除）
     *
     * @param id 待删除文件夹ID
     */
    @Transactional
    public void delete(Long id) {
        CurrentUser user = UserContext.require();
        DriveFolder folder = getActive(id);
        // 根目录禁止删除
        if (folder.getParentId() == 0L) {
            throw new BusinessException("系统根文件夹不能删除");
        }
        // 校验删除权限
        permissionService.checkCanDeleteFolder(user, folder);

        // 查询文件夹下是否存在子文件夹、子文件
        Long childFolders = folderMapper.selectCount(
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getParentId, id)
                        .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name())
        );
        Long childFiles = fileMapper.selectCount(
                new LambdaQueryWrapper<DriveFile>()
                        .eq(DriveFile::getFolderId, id)
                        .eq(DriveFile::getStatus, DataStatus.ACTIVE.name())
        );
        // 文件夹内存在内容，禁止删除
        if (childFolders > 0 || childFiles > 0) {
            throw new BusinessException("文件夹非空，不能删除");
        }
        LocalDateTime now = LocalDateTime.now();
        // 逻辑删除：修改状态为DELETED，不执行物理删除
        folder.setStatus(DataStatus.DELETED.name());
        folder.setUpdatedAt(now);
        folder.setDeletedAt(now);
        folderMapper.updateById(folder);
    }

    //文件夹恢复
    @Transactional
    public FolderView restore(Long id) {
        CurrentUser user = UserContext.require();
        DriveFolder folder = getDeleted(id);
        permissionService.checkCanDeleteFolder(user, folder);
        //父目录必须仍然正常存在
        DriveFolder parent = getActive(folder.getParentId());
        permissionService.checkCanCreateFolder(user, parent);
        //同目录里不能存在同名文件夹
        ensureNoDuplicate(folder.getParentId(), folder.getFolderName(), folder.getId());
        folder.setStatus(DataStatus.ACTIVE.name());
        folder.setDeletedAt(null);
        folder.setUpdatedAt(LocalDateTime.now());
        folderMapper.updateById(folder);
        return toView(folder);
    }

    //文件夹回收站列表
    public List<TrashFolderView> listTrash() {
        CurrentUser user = UserContext.require();
        return folderMapper.selectList(
                        new LambdaQueryWrapper<DriveFolder>()
                                .eq(DriveFolder::getStatus, DataStatus.DELETED.name())
                                .orderByDesc(DriveFolder::getDeletedAt)
                ).stream()
                .filter(folder ->{
                    try{
                        permissionService.checkCanDeleteFolder(user,folder);
                        return true;
                    }catch (BusinessException e){
                        return false;
                    }
                })
                .map(this::toTrashView)
                .toList();
    }

    /**
     * 根据ID查询【有效、未删除】的文件夹
     *
     * @param id 文件夹主键
     * @return 文件夹实体
     */
    public DriveFolder getActive(Long id) {
        DriveFolder folder = folderMapper.selectOne(
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getId, id)
                        .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name())
        );
        if (folder == null) {
            throw new BusinessException(400, "文件夹不存在");
        }
        return folder;
    }

    /**
     * 校验同级目录不存在同名文件夹
     *
     * @param parentId   父文件夹ID
     * @param name       目标文件夹名称
     * @param excludedId 需要排除的文件夹ID（修改名称时传入自身id，避免自己和自己重名）
     */
    private void ensureNoDuplicate(Long parentId, String name, Long excludedId) {
        LambdaQueryWrapper<DriveFolder> wrapper =
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getParentId, parentId)
                        .eq(DriveFolder::getFolderName, name)
                        .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name());
        // 修改操作：排除当前文件夹自身
        if (excludedId != null) {
            wrapper.ne(DriveFolder::getId, excludedId);
        }
        if (folderMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("同级目录下已存在同名文件夹");
        }
    }

    /**
     * 规范化文件夹名称，清洗并校验非法字符
     *
     * @param value 原始文件夹名称
     * @return 清洗后的合法文件夹名称
     */
    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "文件夹名称不能为空");
        }
        // 禁止路径相关符号，防止路径穿越漏洞
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new BusinessException(400, "文件夹名称不能包含路径符号");
        }
        return name;
    }

    /**
     * 实体转换：数据库Entity → 返回前端VO
     *
     * @param folder 数据库DriveFolder实体
     * @return 对外展示FolderView视图对象
     */
    private FolderView toView(DriveFolder folder) {
        return new FolderView(
                folder.getId(),
                folder.getParentId(),
                folder.getDepartmentId(),
                folder.getFolderName(),
                folder.getAreaType(),
                folder.getOwnerId(),
                folder.getCreatedAt()
        );
    }

    //无论状态的文件夹
    public DriveFolder getById(Long id) {
        return folderMapper.selectById(id);
    }

    //找回收站文件夹：
    public DriveFolder getDeleted(long id) {
        DriveFolder folder = folderMapper.selectOne(
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getId, id)
                        .eq(DriveFolder::getStatus, DataStatus.DELETED.name())
        );
        if (folder == null)
            throw new BusinessException(404, "回收站中不存在该文件夹");
        return folder;
    }
    //文件夹回收站VO
    private TrashFolderView toTrashView(DriveFolder folder) {

        return new TrashFolderView(
                folder.getId(),
                folder.getParentId(),
                folder.getDepartmentId(),
                folder.getFolderName(),
                folder.getAreaType(),
                folder.getOwnerId(),
                folder.getCreatedAt(),
                folder.getDeletedAt()
        );
    }

}