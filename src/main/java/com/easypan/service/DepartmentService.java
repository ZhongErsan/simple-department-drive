package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.auth.CurrentUser;
import com.easypan.auth.UserContext;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.dto.CreateDepartmentRequest;
import com.easypan.model.dto.UpdatedDepartmentRequest;
import com.easypan.model.entity.SysDepartment;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    @Transactional
    public SysDepartment creat(CreateDepartmentRequest request){
        requireAdmin();
        String name=request.departmentName().trim();
        ensureNameAvailable(name,null);
        LocalDateTime now=LocalDateTime.now();
        SysDepartment department=new SysDepartment();
        department.setDepartmentName(name);
        department.setStatus(DataStatus.ACTIVE.name());
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        department.setQuotaBytes(request.quotaBytes());
        department.setUsedBytes(0L);
        //解决并发重名问题
        try {
            int affectedRows = departmentMapper.insert(department);

            if (affectedRows != 1) {
                throw new BusinessException(500, "部门创建失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "部门名称已存在");
        }
        return department;
    }
    @Transactional
    public List<SysDepartment> list(){
        return departmentMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>().orderByAsc(SysDepartment::getId)
        );
    }

    public SysDepartment get(Long id){
        SysDepartment department=departmentMapper.selectById(id);
        if(department==null || DataStatus.DELETED.name()
                .equals(department.getStatus())){
            throw new BusinessException(404,"部门不存在");
        }
        return department;
    }
    @Transactional
    public SysDepartment update(Long id, UpdatedDepartmentRequest request){
        requireAdmin();
        //先根据 id 查询该部门是否存在
        get(id);
        String name=request.departmentName().trim();
        ensureNameAvailable(name,id);
        //更新
        LocalDateTime now = LocalDateTime.now();

        try {
            int affectedRows = departmentMapper.updateDepartmentConditionally(
                    id,
                    name,
                    request.quotaBytes(),
                    now
            );

            if (affectedRows == 0) {
                throw new BusinessException(
                        409,
                        "部门已用容量发生变化，配额修改失败，请刷新后重试"
                );
            }
            //解决并发重名问题
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    409,
                    "部门名称已存在"
            );
        }

        // 重新查询并返回数据库中的最新数据
        return get(id);
    }
    @Transactional
    public void disable(long id){
        requireAdmin();
        SysDepartment department=get(id);
        Long activeUsers=userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDepartmentId,id)
                        .eq(SysUser::getStatus,DataStatus.ACTIVE.name())
        );
        if(activeUsers>0){
            throw new BusinessException(400,"部门下仍有启用用户，不能禁用");
        }
        department.setStatus(DataStatus.DISABLED.name());
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);
    }
    private void requireAdmin(){
        CurrentUser user= UserContext.require();
        if(!user.isAdmin()){
            throw new BusinessException(403,"只有管理员可以执行此操作");
        }
    }
    private void ensureNameAvailable(String name,Long excludedId){
        LambdaQueryWrapper<SysDepartment> wrapper=new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getDepartmentName,name);
        if(excludedId!=null)
            wrapper.ne(SysDepartment::getId,excludedId);
        if(departmentMapper.selectCount(wrapper)>0){
            throw new BusinessException(400,"部门名称已存在");
        }
    }
}
