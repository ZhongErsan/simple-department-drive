# 简化需求摘要

## 角色

- ADMIN：操作所有部门和区域。
- MINISTER：管理本部门公共区、投稿区，以及自己的个人区。
- MEMBER：公共区只读；投稿区可上传；管理自己的个人区。

## 核心表

- `sys_department`
- `sys_user`
- `drive_folder`
- `drive_file`

## 接口

```text
POST   /api/auth/login

POST   /api/departments
GET    /api/departments
GET    /api/departments/{id}
PUT    /api/departments/{id}
DELETE /api/departments/{id}

POST   /api/users
GET    /api/users
GET    /api/users/{id}
PUT    /api/users/{id}
PUT    /api/users/{id}/password
DELETE /api/users/{id}

GET    /api/folders?parentId=0
POST   /api/folders
PUT    /api/folders/{id}
DELETE /api/folders/{id}

POST   /api/files/upload
GET    /api/files?folderId=1
GET    /api/files/{id}/download
DELETE /api/files/{id}
```

## 第一遍不做

配额、Tika、SHA-256、批量和分片上传、在线预览、Redis、消息队列、复杂日志和极端事务补偿。
