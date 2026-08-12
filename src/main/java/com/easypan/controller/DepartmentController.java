package com.easypan.controller;

import com.easypan.common.Result;
import com.easypan.model.dto.CreateDepartmentRequest;
import com.easypan.model.dto.UpdatedDepartmentRequest;
import com.easypan.model.entity.SysDepartment;
import com.easypan.service.DepartmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;
    @PostMapping
    public Result<SysDepartment> create(@Valid @RequestBody CreateDepartmentRequest request){
        return Result.success("部门创建成功",departmentService.creat(request));
    }
    @GetMapping
    public Result<List<SysDepartment>> list(){
        return Result.success(departmentService.list());
    }
    @GetMapping("/{id}")
    public Result<SysDepartment> get(@PathVariable Long id){
        return Result.success(departmentService.get(id));
    }
    @PutMapping("/{id}")
    public Result<SysDepartment> update(@PathVariable Long id, @Valid @RequestBody UpdatedDepartmentRequest request){
        return Result.success("部门修改成功",departmentService.update(id,request));
    }
    @DeleteMapping("/{id}")
    public Result<Void> diable(@PathVariable Long id){
        departmentService.disable(id);
        return Result.success("部门已禁用");
    }
}
