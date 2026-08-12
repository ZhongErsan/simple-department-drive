package com.easypan.simpledepartmentdrive;

import com.easypan.auth.CurrentUser;
import com.easypan.exception.BusinessException;
import com.easypan.model.entity.DriveFolder;
import com.easypan.model.enums.AreaType;
import com.easypan.model.enums.Role;
import com.easypan.service.DrivePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SimpleDepartmentDriveApplicationTests {

    private DrivePermissionService service;
    private CurrentUser member;

    @BeforeEach
    void setUp() {
        service = new DrivePermissionService();
        member = new CurrentUser(10L, "member", "成员", Role.MEMBER, 2L);
    }

    @Test
    void memberCanUploadToContributionArea() {
        assertDoesNotThrow(
                () -> service.checkCanUpload(member, folder(AreaType.CONTRIBUTION, null, 2L))
        );
    }

    @Test
    void memberCannotUploadToPublicArea() {
        assertThrows(
                BusinessException.class,
                () -> service.checkCanUpload(member, folder(AreaType.PUBLIC, null, 2L))
        );
    }

    @Test
    void memberCanUseOwnPersonalArea() {
        assertDoesNotThrow(
                () -> service.checkCanUpload(member, folder(AreaType.PERSONAL, 10L, 2L))
        );
    }

    @Test
    void memberCannotUseAnotherPersonalArea() {
        assertThrows(
                BusinessException.class,
                () -> service.checkCanUpload(member, folder(AreaType.PERSONAL, 11L, 2L))
        );
    }

    private DriveFolder folder(AreaType area, Long ownerId, Long departmentId) {
        DriveFolder folder = new DriveFolder();
        folder.setAreaType(area.name());
        folder.setOwnerId(ownerId);
        folder.setDepartmentId(departmentId);
        return folder;
    }

}
