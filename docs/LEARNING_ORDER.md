# 第一遍学习和重写顺序

每个阶段都做同样的六件事：

1. 运行现有接口。
2. 写出输入和输出。
3. 写出涉及的表。
4. 写出权限条件。
5. 写出正常流程。
6. 写出至少一个失败流程。

然后把该模块源码临时移走，自己重新创建并手写。

## 阶段一：统一返回和异常

阅读：

```text
Result
→ 四个业务异常
→ GlobalExceptionHandler
→ TestController
```

目标：自己写出 `/api/test`，理解为什么 Service 抛异常、Controller 不到处 try-catch。

## 阶段二：部门 CRUD

阅读：

```text
SysDepartment
→ SysDepartmentMapper
→ DepartmentService
→ DepartmentController
```

先只重写新增和详情，再写列表、修改和禁用。

## 阶段三：用户 CRUD

重点：

- 用户名重名
- BCrypt
- 角色枚举
- 用户和部门关系
- 创建用户后创建个人空间

## 阶段四：JWT

阅读：

```text
AuthController
→ AuthService
→ JwtService
→ AuthInterceptor
→ UserContext
→ WebMvcConfig
```

必须能解释 Token 从哪里来、谁校验、当前用户放在哪里，以及为什么请求结束后要清理 ThreadLocal。

## 阶段五：权限

重点文件：

```text
DrivePermissionService
```

用下面五个条件理解每次判断：

```text
角色 + 部门 + 区域 + ownerId + uploaderId
```

## 阶段六：文件夹

理解：

- `parentId`
- 根目录为什么是 0
- 子目录为什么继承部门、区域和 ownerId
- 非空文件夹为什么不能删除

## 阶段七：上传

先自己写最小版本：

```text
接收 MultipartFile
→ 判空
→ UUID 文件名
→ 保存本地
→ 插入数据库
```

再依次增加权限、文件名、扩展名、大小、同名检查和失败清理。

## 第二遍 Agent 任务

不要让 Agent 一次重写全项目，逐项要求：

1. 用 UserVO 替代实体返回。
2. 增加分页。
3. 消除 N+1 查询。
4. 增加数据库级并发重名保护。
5. 增加 Tika MIME。
6. 增加 SHA-256。
7. 增加个人和部门配额。
8. 增加事务完成回调和孤儿文件清理。
9. 增加回收站和永久删除。
10. 增加集成测试。
