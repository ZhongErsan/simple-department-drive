package com.easypan.service;

import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysDepartmentMapper;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.DriveFile;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.entity.SysDepartment;
import com.easypan.model.enums.AreaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuotaService {
    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;

    //占用存储空间配额
    public void consume(DriveFolder folder, long bytes) {
        validateBytes(bytes);
        AreaType areaType = AreaType.valueOf(folder.getAreaType());
        if (areaType == AreaType.PERSONAL) {
            consumePersonalQuota(folder, bytes);
            return;
        }
        consumeDepartmentQuota(folder,bytes);
    }
    //释放存储空间配额
    public void release(DriveFolder folder,long bytes){
        validateBytes(bytes);
        AreaType areaType=AreaType.valueOf(folder.getAreaType());
        if(areaType==AreaType.PERSONAL){
            releasePersonalQuota(folder,bytes);
            return;
        }
        releaseDepartmentQuota(folder,bytes);

    }
    //释放个人空间配额
    private void releasePersonalQuota(DriveFolder folder,long bytes){
        Long ownerId=folder.getOwnerId();
        if(ownerId==null)
            throw new BusinessException(500,"个人空间缺少所有者信息");
        int affected=userMapper.releaseQuota(ownerId,bytes);
        if(affected!=1)
            throw new BusinessException(500,"个人配额释放失败");
    }

    //释放部门空间配额
    private void releaseDepartmentQuota(DriveFolder folder,long bytes){
        int affected=departmentMapper.releaseQuota(folder.getDepartmentId(),bytes);
        if(affected!=1)
            throw new BusinessException(500,"部门配额释放失败");
    }

    //参数校验：配额字节不能为负数
    private void validateBytes(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("配额字节数不能为负数");
    }

    //占用个人空间配额
    private void consumePersonalQuota(DriveFolder folder, long bytes) {
        Long ownerId = folder.getOwnerId();
        if (ownerId == null)
            throw new BusinessException(500, "个人空间缺少所有者信息");
        int affected = userMapper.tryConsumeQuota(ownerId, bytes);
        if (affected != 1) {
            throw new BusinessException(413, "个人空间配额不足");
        }
    }

    //占用部门空间配额
    private void consumeDepartmentQuota(DriveFolder folder, long bytes) {
        int affected = departmentMapper.tryConsumeQuota(folder.getDepartmentId(), bytes);
        if (affected != 1)
            throw new BusinessException(413, "部门空间配额不足");
    }
}
