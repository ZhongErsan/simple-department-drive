package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easypan.auth.CurrentUser;
import com.easypan.auth.UserContext;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.DriveFileMapper;
import com.easypan.mapper.DriveFolderMapper;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.dto.CreateUserRequest;
import com.easypan.model.dto.ResetPasswordRequest;
import com.easypan.model.dto.UpdateUserRequest;
import com.easypan.model.entity.DriveFile;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.entity.SysDepartment;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.AreaType;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.enums.Role;
import com.easypan.model.vo.PageResult;
import com.easypan.model.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {
    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final DriveFolderMapper driveFolderMapper;
    private final DriveFileMapper driveFileMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserVO create(CreateUserRequest request) {
        requireAdmin();
        Role role = parseRole(request.role());
        validateDepartment(role, request.departmentId());
        String userName = request.username();
        //前置重名校验
        if (userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, userName)
        ) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(userName);
        user.setRealName(request.realName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role.name());
        user.setDepartmentId(request.departmentId());
        user.setStatus(DataStatus.ACTIVE.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setQuotaBytes(request.quotaBytes());
        user.setUsedBytes(0L);
        //解决并发重名问题
        try {
            int affectedRows = userMapper.insert(user);

            if (affectedRows != 1) {
                throw new BusinessException(500, "用户创建失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户名已存在");
        }
        //创建用户个人文件夹
        if (user.getDepartmentId() != null) {
            createPersonalRoot(user, now);
        }
        return toVO(user);
    }

    @Transactional
    public PageResult<UserVO> list(long pageNum, long pageSize) {
        CurrentUser currentUser = UserContext.require();
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId);
        if (currentUser.isAdmin()) {
        } else if (currentUser.isMinister()) {
            query.eq(SysUser::getDepartmentId, currentUser.departmentId())
                    .orderByAsc(SysUser::getId);
        } else {
            throw new BusinessException(403, "普通部员不能访问用户管理列表");
        }
        // 构建分页参数对象
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        // 执行分页查询，根据上面构造的条件查询数据库
        IPage<SysUser> userIPage = userMapper.selectPage(page, query);
        //实体SysUser转换为前端展示VO
        List<UserVO> records = userIPage.getRecords().stream().map(this::toVO).toList();

        return new PageResult<>(
                userIPage.getCurrent(),
                userIPage.getSize(),
                userIPage.getTotal(),
                userIPage.getPages(),
                records
        );

    }

    @Transactional
    public UserVO get(Long id) {
        CurrentUser currentUser = UserContext.require();
        SysUser target = getRequired(id);
        boolean sameDepartmentMinister = currentUser.isMinister()
                && currentUser.departmentId() != null
                && currentUser.departmentId().equals(target.getDepartmentId());
        if (currentUser.isAdmin() || currentUser.userId().equals(target.getId()) || sameDepartmentMinister)
            return toVO(target);
        throw new BusinessException(403, "无权查看该用户");
    }

    @Transactional
    public UserVO update(Long id, UpdateUserRequest request) {
        requireAdmin();
        SysUser user = getRequired(id);
        Role role = parseRole(request.role());
        DataStatus status = parseUserStatus(request.status());
        validateDepartment(role, request.departmentId());
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = userMapper.updateUserConditionally(
                id,
                request.realname(),
                role.name(),
                request.departmentId(),
                status.name(),
                request.quotaBytes(),
                now
        );
        if (affectedRows == 0) {
            // 因为前面已经确认用户存在，所以这里通常就是配额条件未满足。
            throw new BusinessException(
                    409,
                    "个人配额不能小于当前已用容量"
            );
        }
        Long oldDepartmentId = user.getDepartmentId();
        Long newDepartmentId = request.departmentId();

        if (newDepartmentId != null
                && !Objects.equals(newDepartmentId, oldDepartmentId)) {
            migratePersonalDepartment(id, newDepartmentId);
        }
        //返回重新查询的用户
        SysUser updatedUser = getRequired(id);

        if (updatedUser.getDepartmentId() != null) {
            ensurePersonalRoot(updatedUser);
        }

        return toVO(updatedUser);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        requireAdmin();
        //先确认目标用户确实存在
        getRequired(id);
        String encodedPassword=passwordEncoder.encode(request.newPassword());
        int affectedRows=userMapper.resetPasswordAndClearSession(id,encodedPassword);
        if(affectedRows!=1)
            throw new BusinessException(500,"密码重置失败");

    }

    @Transactional
    public void disable(Long id) {
        requireAdmin();
        CurrentUser currentUser = UserContext.require();
        if (currentUser.userId().equals(id))
            throw new BusinessException(403, "不能禁用当前登录账号");
        getRequired(id);
        int affectedRows=userMapper.disabledAndClearSession(id);
        if(affectedRows!=1)
            throw new BusinessException(500,"禁用用户失败");
    }

    private void migratePersonalDepartment(
            Long ownerId,
            Long newDepartmentId
    ) {
        LocalDateTime now = LocalDateTime.now();

        driveFolderMapper.migratePersonalFolders(
                ownerId,
                newDepartmentId,
                now
        );

        driveFileMapper.migratePersonalFiles(
                ownerId,
                newDepartmentId,
                now
        );

    }

    private void requireAdmin() {
        if (!UserContext.require().isAdmin())
            throw new BusinessException(403, "只有管理员可以执行此操作");
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(400, "角色只能是ADMIN、MINISTER、或MEMBER");
        }
    }

    private DataStatus parseUserStatus(String value) {
        try {
            DataStatus status = DataStatus.valueOf(value.trim().toUpperCase());
            if (status != DataStatus.ACTIVE && status != DataStatus.DISABLED) {
                //枚举匹配成功，但不是启用 / 禁用 → 手动抛出 IllegalArgumentException
                throw new IllegalArgumentException();
            }
            return status;
        } catch (Exception e) {
            throw new BusinessException(400, "用户状态只能是ACTIVE或DISABLED");
        }
    }

    private void validateDepartment(Role role, Long departmentId) {
        if (Role.ADMIN.equals(role))
            return;
        if (departmentId == null) {
            throw new BusinessException(400, "部长和普通成员必须分配部门");
        }
        if (departmentId != null) {
            SysDepartment department = departmentMapper.selectById(departmentId);
            if (department == null || !DataStatus.ACTIVE.name().equals(department.getStatus())) {
                throw new BusinessException(404, "部门不存在或已被禁用");
            }
        }
    }

    private SysUser getRequired(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private void createPersonalRoot(SysUser user, LocalDateTime now) {
        DriveFolder folder = new DriveFolder();
        folder.setDepartmentId(user.getDepartmentId());
        folder.setParentId(0L);
        folder.setFolderName(user.getRealName() + "的个人空间");
        folder.setAreaType(AreaType.PERSONAL.name());
        folder.setOwnerId(user.getId());
        folder.setCreatedBy(user.getId());
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        driveFolderMapper.insert(folder);
    }

    private void ensurePersonalRoot(SysUser user) {
        Long count = driveFolderMapper.selectCount(
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getOwnerId, user.getId())
                        .eq(DriveFolder::getAreaType, AreaType.PERSONAL.name())
                        .eq(DriveFolder::getParentId, 0L)
                        .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name())
        );
        if (count == 0) {
            createPersonalRoot(user, LocalDateTime.now());
        }
    }

    private UserVO toVO(SysUser user) {
        long quotaBytes = user.getQuotaBytes() == null ? 0L : user.getQuotaBytes();
        long usedBytes = user.getUsedBytes() == null ? 0L : user.getUsedBytes();
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                user.getDepartmentId(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                quotaBytes,
                usedBytes,
                Math.max(quotaBytes - usedBytes, 0L)
        );
    }
}
