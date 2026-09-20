package com.easypan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easypan.auth.CurrentUser;
import com.easypan.auth.UserContext;
import com.easypan.cache.CacheInvalidationRegistrar;
import com.easypan.cache.CacheKeys;
import com.easypan.cache.CacheProperties;
import com.easypan.cache.RedisDataCacheService;
import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.dto.CreateDepartmentRequest;
import com.easypan.model.dto.UpdatedDepartmentRequest;
import com.easypan.model.entity.SysDepartment;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final RedisDataCacheService cacheService;
    private final CacheProperties cacheProperties;
    private final CacheInvalidationRegistrar cacheInvalidationRegistrar;

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
            cacheInvalidationRegistrar.evictAfterCommit(
                    CacheKeys.DEPARTMENT_LIST
            );
            if (affectedRows != 1) {
                throw new BusinessException(500, "部门创建失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "部门名称已存在");
        }
        return department;
    }
    /**
     * 查询全部部门列表（带全量缓存）
     * 注意：这个方法上加了 @Transactional，但**这个查询本身并不需要事务**，属于多余注解
     */
    @Transactional
    public List<SysDepartment> list(){
        // 1. 先从Redis读取缓存，DEPARTMENT_LIST是固定key
        // TypeReference用来解决泛型List<SysDepartment>的序列化问题
        List<SysDepartment> cached=cacheService.get(CacheKeys.DEPARTMENT_LIST, new TypeReference<List<SysDepartment>>() {
        });
        // 缓存命中，直接返回，不访问数据库
        if(cached!=null)
            return cached;

        // 2.缓存没命中，查询MySQL：查询所有部门，按id升序
        List<SysDepartment> departments=departmentMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>().orderByAsc(SysDepartment::getId)
        );
        // 3.把数据库查到的部门全量列表写入Redis，设置TTL（2分钟）
        cacheService.set(CacheKeys.DEPARTMENT_LIST,departments,cacheProperties.departmentTtl());
        return departments;
    }


    public SysDepartment get(Long id){
        String key =CacheKeys.departmentDetail(id);

        /*
         * 1. 先查 Redis
         */
        SysDepartment department =
                cacheService.get(
                        key,
                        SysDepartment.class
                );

        /*
         * 2. Redis 未命中，再查数据库
         */
        if (department == null) {

            department =departmentMapper.selectById(id);

            /*
             * 数据库存在并且没有删除，
             * 才放入缓存。
             */
            if (department != null
                    && !DataStatus.DELETED.name()
                    .equals(department.getStatus())) {

                cacheService.set(
                        key,
                        department,
                        cacheProperties.departmentTtl()
                );
            }
        }

        /*
         * 3. 统一校验
         */
        if (department == null
                || DataStatus.DELETED.name()
                .equals(department.getStatus())) {

            throw new BusinessException(
                    404,
                    "部门不存在"
            );
        }
        return department;
    }
    /**
     * 直接从数据库查询部门，**完全不经过Redis缓存**，并且校验部门有效，不存在/已软删则抛出404异常
     * @param id 部门ID
     * @return 有效的部门实体
     */
    private SysDepartment getRequiredFromDb(
            Long id
    ) {
        // 直接通过mapper查询数据库，不走任何Redis缓存
        SysDepartment department =
                departmentMapper.selectById(id);
        // 判断：记录为空 或者 状态是已删除
        if (
                department == null
                        || DataStatus.DELETED.name()
                        .equals(department.getStatus())
        ) {
            // 抛出业务异常，提示部门不存在
            throw new BusinessException(
                    404,
                    "部门不存在"
            );
        }
        // 返回数据库查到的有效部门
        return department;
    }

    @Transactional
    public SysDepartment update(Long id, UpdatedDepartmentRequest request){
        requireAdmin();
        //先根据 id 查询该部门是否存在
        getRequiredFromDb(id);
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
            cacheInvalidationRegistrar.evictAfterCommit(

                    CacheKeys.departmentDetail(id),

                    CacheKeys.DEPARTMENT_LIST
            );
            //解决并发重名问题
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    409,
                    "部门名称已存在"
            );
        }

        // 重新查询并返回数据库中的最新数据
        return getRequiredFromDb(id);
    }
    @Transactional
    public void disable(long id){
        requireAdmin();
        SysDepartment department=getRequiredFromDb(id);
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
        cacheInvalidationRegistrar.evictAfterCommit(

                CacheKeys.departmentDetail(id),

                CacheKeys.DEPARTMENT_LIST
        );
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
