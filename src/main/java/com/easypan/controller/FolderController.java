package com.easypan.controller;

import com.easypan.common.Result;
import com.easypan.model.dto.CreateFolderRequest;
import com.easypan.model.dto.RenameFolderRequest;
import com.easypan.model.vo.FolderView;
import com.easypan.model.vo.TrashFolderView;
import com.easypan.service.DriveFolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {
    private final DriveFolderService folderService;

    @GetMapping
    public Result<List<FolderView>> list(@RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(folderService.listChildren(parentId));
    }

    @PostMapping
    public Result<FolderView> create(@Valid @RequestBody CreateFolderRequest request) {
        return Result.success("文件夹创建成功", folderService.create(request));
    }

    @PutMapping("/{id}")
    public Result<FolderView> rename(@PathVariable Long id, @Valid @RequestBody RenameFolderRequest request) {
        return Result.success(folderService.rename(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return Result.success("文件夹已删除");
    }

    @GetMapping("/trash")
    public Result<List<TrashFolderView>> trash() {
        return Result.success(folderService.listTrash());
    }

    @PutMapping("/{id}/restore")
    public Result<FolderView> restore(@PathVariable Long id) {
        return Result.success("文件夹恢复成功", folderService.restore(id));
    }


}
