package com.easypan.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.mapper.DriveFolderMapper;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.entity.SysDepartment;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.AreaType;
import com.easypan.model.enums.DataStatus;
import com.easypan.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 项目启动初始化演示数据
 * 实现CommandLineRunner：SpringBoot项目启动完成后自动执行run方法
 * 作用：自动创建测试部门、管理员、部长、普通用户、部门根目录、个人空间根目录
 * 幂等设计：所有数据新增前先查询，存在则跳过，避免重复生成脏数据
 * 可通过配置 app.demo-data-enabled 开关控制是否启用初始化
 */
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {
    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final DriveFolderMapper folderMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 配置文件开关，控制是否初始化演示数据，默认开启
     */
    @Value("${app.demo-data-enabled:true}")
    private boolean enabled;

    /**
     * SpringBoot启动成功后执行的入口方法
     * @param args 启动参数
     */
    @Override
    @Transactional
    public void run(String... args) {
        // 开关关闭，直接跳过初始化
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        // 创建超级管理员账号
        SysUser admin = ensureUser(
                "admin", "admin123", "系统管理员",
                Role.ADMIN, null, now
        );

        // 查询【技术部】部门，不存在则新建
        SysDepartment department = departmentMapper.selectOne(
                new LambdaQueryWrapper<SysDepartment>()
                        .eq(SysDepartment::getDepartmentName, "技术部")
        );
        if (department == null) {
            department = new SysDepartment();
            department.setDepartmentName("技术部");
            department.setStatus(DataStatus.ACTIVE.name());
            department.setCreatedAt(now);
            department.setUpdatedAt(now);
            departmentMapper.insert(department);
        }

        // 技术部基础账号
        SysUser minister = ensureUser(
                "minister", "123456", "技术部部长",
                Role.MINISTER, department.getId(), now
        );
        SysUser member = ensureUser(
                "member", "123456", "普通成员",
                Role.MEMBER, department.getId(), now
        );

        // 批量创建多个部长账号，用于权限分页测试
        SysUser minister1 = ensureUser(
                "minister01", "123456", "部门部长A",
                Role.MINISTER, department.getId(), now
        );
        SysUser minister2 = ensureUser(
                "minister02", "123456", "部门部长B",
                Role.MINISTER, department.getId(), now
        );

        // 批量创建大量普通成员，充足数据用于用户列表分页功能测试
        SysUser member1 = ensureUser(
                "member01", "123456", "普通成员一号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member2 = ensureUser(
                "member02", "123456", "普通成员二号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member3 = ensureUser(
                "member03", "123456", "普通成员三号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member4 = ensureUser(
                "member04", "123456", "普通成员四号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member5 = ensureUser(
                "member05", "123456", "普通成员五号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member6 = ensureUser(
                "member06", "123456", "普通成员六号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member7 = ensureUser(
                "member07", "123456", "普通成员七号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member8 = ensureUser(
                "member08", "123456", "普通成员八号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member9 = ensureUser(
                "member09", "123456", "普通成员九号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member10 = ensureUser(
                "member10", "123456", "普通成员十号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member11 = ensureUser(
                "member11", "123456", "普通成员十一号",
                Role.MEMBER, department.getId(), now
        );
        SysUser member12 = ensureUser(
                "member12", "123456", "普通成员十二号",
                Role.MEMBER, department.getId(), now
        );

        // 创建部门公共根目录：公共区、投稿区（parentId=0 根文件夹）
        ensureSharedRoot(department.getId(), "公共区", AreaType.PUBLIC, admin.getId(), now);
        ensureSharedRoot(department.getId(), "投稿区", AreaType.CONTRIBUTION, admin.getId(), now);
        // 为指定用户创建个人空间根目录
        ensurePersonalRoot(minister, now);
        ensurePersonalRoot(minister1, now);
        ensurePersonalRoot(minister2, now);
        ensurePersonalRoot(member, now);
        ensurePersonalRoot(member1, now);
        ensurePersonalRoot(member2, now);
        ensurePersonalRoot(member3, now);
        ensurePersonalRoot(member4, now);
        ensurePersonalRoot(member5, now);
        ensurePersonalRoot(member6, now);
        ensurePersonalRoot(member7, now);
        ensurePersonalRoot(member8, now);
        ensurePersonalRoot(member9, now);
        ensurePersonalRoot(member10, now);
        ensurePersonalRoot(member11, now);

        ensurePersonalRoot(member12, now);
    }

    /**
     * 保证用户存在（幂等方法）
     * 根据用户名查询用户，不存在则新建；存在直接返回已有用户
     * @param username 登录账号
     * @param password 原始密码（内部自动加密存储）
     * @param realName 用户真实姓名
     * @param role 用户角色
     * @param departmentId 所属部门id，管理员无部门填null
     * @param now 当前时间
     * @return 数据库用户实体
     */
    private SysUser ensureUser(
            String username,
            String password,
            String realName,
            Role role,
            Long departmentId,
            LocalDateTime now
    ) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
        );
        // 用户已存在，直接返回，不重复创建
        if (user != null) {
            return user;
        }

        user = new SysUser();
        user.setUsername(username);
        // 使用BCrypt加密密码存入数据库，不存储明文
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setRole(role.name());
        user.setDepartmentId(departmentId);
        user.setStatus(DataStatus.ACTIVE.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }

    /**
     * 创建【部门共享根文件夹】幂等方法
     * parentId=0 代表根目录，分为公共区、投稿区等共享区域
     * @param departmentId 归属部门
     * @param name 文件夹名称
     * @param area 区域类型（公共/投稿）
     * @param creatorId 创建人ID
     * @param now 当前时间
     */
    private void ensureSharedRoot(
            Long departmentId,
            String name,
            AreaType area,
            Long creatorId,
            LocalDateTime now
    ) {
        // 查询是否已经存在该部门对应类型的共享根目录
        Long count = folderMapper.selectCount(
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getDepartmentId, departmentId)
                        .eq(DriveFolder::getParentId, 0L)
                        .eq(DriveFolder::getAreaType, area.name())
                        .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name())
        );
        // 目录已存在，跳过创建
        if (count > 0) {
            return;
        }

        DriveFolder folder = new DriveFolder();
        folder.setDepartmentId(departmentId);
        folder.setParentId(0L); // 根目录标识
        folder.setFolderName(name);
        folder.setAreaType(area.name());
        folder.setOwnerId(null); // 部门共享目录不属于单个用户
        folder.setCreatedBy(creatorId);
        folder.setStatus(DataStatus.ACTIVE.name());
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        folderMapper.insert(folder);
    }

    /**
     * 创建【用户个人空间根目录】幂等方法
     * 每个用户拥有独立个人空间，parentId=0，区域类型为个人空间
     * @param user 目标用户
     * @param now 当前时间
     */
    private void ensurePersonalRoot(SysUser user, LocalDateTime now) {
        Long count = folderMapper.selectCount(
                new LambdaQueryWrapper<DriveFolder>()
                        .eq(DriveFolder::getOwnerId, user.getId())
                        .eq(DriveFolder::getAreaType, AreaType.PERSONAL.name())
                        .eq(DriveFolder::getParentId, 0L)
                        .eq(DriveFolder::getStatus, DataStatus.ACTIVE.name())
        );
        // 当前用户个人根目录已存在，无需新建
        if (count > 0) {
            return;
        }

        DriveFolder folder = new DriveFolder();
        folder.setDepartmentId(user.getDepartmentId());
        folder.setParentId(0L); // 根目录
        folder.setFolderName(user.getRealName() + "的个人空间");
        folder.setAreaType(AreaType.PERSONAL.name());
        folder.setOwnerId(user.getId()); // 归属当前用户
        folder.setCreatedBy(user.getId());
        folder.setStatus(DataStatus.ACTIVE.name());
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        folderMapper.insert(folder);
    }
}