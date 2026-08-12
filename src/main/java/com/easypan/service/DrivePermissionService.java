package com.easypan.service;

import com.easypan.auth.CurrentUser;
import com.easypan.exception.BusinessException;
import com.easypan.model.entity.DriveFile;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.enums.AreaType;
import org.springframework.stereotype.Service;

@Service
public class DrivePermissionService {

    public boolean canViewFolder(CurrentUser user, DriveFolder folder) {
        if (user.isAdmin())
            return true;
        if (!sameDepartment(user, folder))
            return false;
        AreaType areaType = AreaType.valueOf(folder.getAreaType());
        if (areaType == AreaType.PUBLIC || areaType == AreaType.CONTRIBUTION)
            return true;
        return user.userId().equals(folder.getOwnerId());
    }

    public void checkCanViewFolder(CurrentUser user, DriveFolder folder) {
        if (!canViewFolder(user, folder))
            throw new BusinessException(403, "无权查看该文件夹");
    }

    public void checkCanCreateFolder(CurrentUser user, DriveFolder parent) {
        if (user.isAdmin())
            return;
        if (!sameDepartment(user, parent))
            throw new BusinessException(403, "不能操作其他部门的文件夹");

        AreaType areaType = AreaType.valueOf(parent.getAreaType());

        if (areaType == AreaType.PERSONAL && user.userId().equals(parent.getOwnerId()))
            return;
        if (user.isMinister()
                && (areaType == AreaType.PUBLIC || areaType == AreaType.CONTRIBUTION))
            return;
        throw new BusinessException(403, "当前角色不能在此区域创建文件夹");
    }

    public void checkCanUpload(CurrentUser user, DriveFolder folder) {
        if (user.isAdmin())
            return;
        if (!sameDepartment(user, folder))
            throw new BusinessException(403, "不能上传到其他部门");
        AreaType areaType = AreaType.valueOf(folder.getAreaType());

        if (areaType == AreaType.PERSONAL && user.userId().equals(folder.getOwnerId()))
            return;
        if (areaType == AreaType.CONTRIBUTION)
            return;
        if (areaType == AreaType.PUBLIC && user.isMinister())
            return;
        throw new BusinessException(403, "当前角色不能上传到此区域");
    }

    public void checkCanDeleteFolder(CurrentUser user, DriveFolder folder) {
        checkCanCreateFolder(user, folder);
    }

    public void checkCanDeleteFile(CurrentUser user, DriveFolder folder, DriveFile file) {
        if (user.isAdmin())
            return;
        if (!sameDepartment(user, folder))
            throw new BusinessException(403, "不能删除其他部门的文件");
        AreaType areaType = AreaType.valueOf(folder.getAreaType());
        if (areaType == AreaType.PERSONAL && user.userId().equals(folder.getOwnerId()))
            return;
        if (user.isMinister()
                && (areaType == AreaType.PUBLIC || areaType == AreaType.CONTRIBUTION))
            return;
        if (user.isMember()
                && areaType == AreaType.CONTRIBUTION
                && user.userId().equals(file.getUploaderId())) {
            return;
        }
        throw new BusinessException(403,"无权删除该文件夹");
    }

    private boolean sameDepartment(CurrentUser user, DriveFolder folder) {
        return user.departmentId() != null
                && user.departmentId().equals(folder.getDepartmentId());
    }
}
