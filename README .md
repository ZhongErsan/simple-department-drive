# Simple Department Drive

一个基于 **Spring Boot + MyBatis-Plus + MySQL + Vue 3** 的简易部门云盘 / 部门文件管理系统。

项目采用前后端分离开发方式，但前端与后端代码放在同一个仓库中。系统围绕“部门、用户、文件夹、文件”四个核心对象展开，支持角色权限控制、个人空间、部门公共区、投稿区、文件上传下载、回收站、个人/部门配额，以及基于 `JWT + Session ID` 的异地登录踢下线。

---

## 项目功能

### 认证与登录

- 用户名、密码登录
- BCrypt 密码加密
- JWT Bearer Token 身份认证
- 登录状态写入 `current_session_id`
- 同一账号再次登录时覆盖旧 Session ID
- 旧 Token 再次请求接口时返回 `401`
- 主动退出登录后当前 Token 立即失效
- 管理员重置密码后用户原登录状态立即失效
- 管理员禁用用户后用户原登录状态立即失效
- ThreadLocal 保存当前请求用户，并在请求结束后清理

### 用户与角色

系统目前包含三种角色：

| 角色 | 说明 |
| --- | --- |
| `ADMIN` | 系统管理员，可以管理所有部门、用户和文件区域 |
| `MINISTER` | 部门部长，可以查看本部门用户并管理本部门公共区、投稿区和自己的个人空间 |
| `MEMBER` | 普通成员，可以查看本部门公共区/投稿区，管理自己的个人空间，并向投稿区上传文件 |

### 文件空间

系统支持三种文件区域：

| 区域 | 说明 |
| --- | --- |
| `PUBLIC` | 部门公共区 |
| `PERSONAL` | 用户个人空间 |
| `CONTRIBUTION` | 部门投稿区 |

主要能力：

- 多级文件夹浏览
- 创建文件夹
- 文件夹重命名
- 删除空文件夹
- 文件上传
- 文件下载
- 文件分页查询
- 文件逻辑删除
- 回收站恢复
- 文件永久删除
- Apache Tika MIME 类型检测
- SHA-256 文件哈希
- 单文件最大 50 MB

### 配额

- 用户个人配额 `quota_bytes / used_bytes`
- 部门共享配额 `quota_bytes / used_bytes`
- 上传时使用 SQL 原子更新占用配额
- 删除 / 永久删除时释放对应配额
- 修改配额时不允许小于当前已使用容量

### 文件清理

项目包含持久化文件清理机制：

- 文件数据库事务失败后的清理补偿
- `storage_cleanup_task` 清理任务表
- 孤儿文件扫描
- 删除失败自动重试
- 指数退避
- 可配置扫描周期与批量大小

---

## 技术栈

### 后端

- Java 17
- Spring Boot 3.5.16
- Spring Web
- Spring Validation
- MyBatis-Plus 3.5.13
- MySQL
- JJWT 0.13.0
- Spring Security Crypto
- Apache Tika 3.3.2
- Lombok
- Maven

### 前端

- Vue 3
- Vite 6
- Vue Router
- Element Plus
- Axios

---

## 项目结构

```text
simple-department-drive/
├── pom.xml
├── package.json
├── vite.config.js
├── docker-compose.yml
├── requests.http
├── docs/
│   ├── KNOWN_LIMITATIONS.md
│   ├── LEARNING_ORDER.md
│   └── PROJECT_REQUIREMENTS.md
├── src/
│   ├── main/
│   │   ├── java/com/easypan/
│   │   │   ├── auth/          # JWT、Session、登录上下文、认证拦截器
│   │   │   ├── common/        # 统一响应
│   │   │   ├── config/        # Spring 配置、演示数据
│   │   │   ├── controller/    # REST API
│   │   │   ├── exception/     # 全局异常处理
│   │   │   ├── mapper/        # MyBatis-Plus Mapper
│   │   │   ├── model/         # DTO、Entity、Enum、VO
│   │   │   ├── service/       # 业务层
│   │   │   └── storage/       # 本地文件存储
│   │   └── resources/
│   │       └── application.yml
│   ├── api/                   # 前端 API
│   ├── layout/                # 前端布局
│   ├── router/                # Vue Router
│   ├── store/                 # 登录状态
│   ├── styles/                # 全局样式
│   ├── utils/                 # HTTP 与格式化工具
│   └── views/                 # 页面
└── storage/                   # 本地文件存储目录（运行时）
```

---

## 运行环境

建议准备：

- JDK 17
- MySQL 8.x
- Node.js 18+
- npm
- Maven（项目已包含 Maven Wrapper）
- Docker / Docker Compose（可选）

---

## 数据库说明

项目当前已经移除了 `schema.sql`，并且配置为：

```yaml
spring:
  sql:
    init:
      mode: never
```

因此：

> **Spring Boot 启动时不会自动创建数据库表。**

启动项目之前，需要确保 MySQL 中已经存在完整的表结构。

当前代码至少依赖以下数据表：

```text
sys_department
sys_user
drive_folder
drive_file
storage_cleanup_task
```

其中登录踢下线功能还依赖：

```text
sys_user.current_session_id
```

配额功能依赖：

```text
sys_user.quota_bytes
sys_user.used_bytes

sys_department.quota_bytes
sys_department.used_bytes
```

文件与回收站功能还会使用 SHA-256、删除时间、物理存储路径等字段。

### 使用 Docker 启动 MySQL

项目提供：

```text
docker-compose.yml
```

启动：

```bash
docker compose up -d
```

默认会创建：

```text
数据库：simple_drive
用户名：root
密码：root
端口：3306
```

需要注意：**Docker Compose 只会创建 MySQL 和 `simple_drive` 数据库，不会创建项目表。**

另外，当前 `application.yml` 的默认数据库密码为：

```text
123
```

而 `docker-compose.yml` 的 MySQL 密码是：

```text
root
```

因此使用 Docker MySQL 时，需要将后端密码改成 `root`，或者通过环境变量启动：

### Linux / macOS

```bash
export DB_PASSWORD=root
./mvnw spring-boot:run
```

### Windows PowerShell

```powershell
$env:DB_PASSWORD="root"
.\mvnw.cmd spring-boot:run
```

也可以直接把两边的数据库密码修改为一致。

---

## 后端配置

主要配置位于：

```text
src/main/resources/application.yml
```

常用环境变量：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | 后端端口 |
| `DB_URL` | `jdbc:mysql://localhost:3306/simple_drive...` | MySQL 地址 |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `123` | 数据库密码 |
| `JWT_SECRET` | 开发默认密钥 | JWT 签名密钥 |
| `JWT_EXPIRATION_SECONDS` | `86400` | Token 有效期，默认 24 小时 |
| `DEMO_DATA_ENABLED` | `true` | 是否初始化演示数据 |
| `STORAGE_ROOT` | `./storage` | 文件存储目录 |
| `STORAGE_CLEANUP_ENABLED` | `true` | 是否开启文件清理任务 |

生产环境请务必通过环境变量设置自己的：

```text
DB_PASSWORD
JWT_SECRET
STORAGE_ROOT
```

不要继续使用开发环境默认密钥和密码。

---

## 启动后端

项目根目录执行：

### Linux / macOS

```bash
./mvnw spring-boot:run
```

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

默认后端地址：

```text
http://localhost:8082
```

打包：

```bash
./mvnw clean package
```

打包完成后：

```bash
java -jar target/easypan-1.0.0.jar
```

---

## 启动前端

先安装依赖：

```bash
npm install
```

启动开发服务器：

```bash
npm run dev
```

默认地址：

```text
http://localhost:5173
```

Vite 已配置代理：

```text
/api
↓
http://localhost:8082
```

所以本地开发时前端可以直接请求：

```text
/api/auth/login
/api/files
/api/folders
```

无需在代码中写死后端完整地址。

### 前端生产打包

```bash
npm run build
```

生成：

```text
dist/
```

---

## 推荐启动顺序

```text
1. 启动 MySQL
2. 确认 simple_drive 数据库和数据表已经存在
3. 启动 Spring Boot 后端
4. 启动 Vue 前端
5. 浏览器访问 http://localhost:5173
```

---

## 演示账号

当：

```yaml
app:
  demo-data-enabled: true
```

并且数据库表已经准备好时，项目启动后会自动补充演示数据。

常用账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 部门部长 | `minister` | `123456` |
| 普通成员 | `member` | `123456` |

初始化器还会创建多个 `minister01`、`member01` 等账号用于分页与权限测试。

> 演示数据初始化是幂等的：已有相同用户名或根目录时不会重复创建。

---

## 登录认证流程

登录成功后，后端会：

```text
校验用户名和密码
        ↓
生成新的 UUID Session ID
        ↓
写入 sys_user.current_session_id
        ↓
Session ID 写入 JWT 的 sid Claim
        ↓
返回 JWT
```

后续请求携带：

```http
Authorization: Bearer <token>
```

`AuthInterceptor` 会同时校验：

```text
JWT 是否有效
用户是否存在
用户是否 ACTIVE
JWT.sid 是否等于数据库 current_session_id
```

### 异地登录踢下线

如果账号在设备 A 登录：

```text
current_session_id = SID-A
```

随后同一账号又在设备 B 登录：

```text
current_session_id = SID-B
```

设备 A 的旧 Token 仍携带 `SID-A`，下一次请求时：

```text
JWT.sid = SID-A
DB.sid  = SID-B
```

后端返回：

```text
401 账号已在其他设备登录，请重新登录
```

前端收到 `401` 后会清理本地 Token，并跳转回登录页。

---

## 权限规则

### ADMIN

- 管理所有部门
- 创建 / 修改 / 禁用用户
- 重置用户密码
- 管理所有文件区域
- 查看和操作所有部门文件

### MINISTER

- 查看本部门用户
- 查看本部门公共区与投稿区
- 管理公共区文件
- 管理投稿区文件
- 管理自己的个人空间

### MEMBER

- 查看本部门公共区
- 查看本部门投稿区
- 向投稿区上传文件
- 删除自己上传到投稿区的文件
- 管理自己的个人空间
- 不能访问用户管理列表

---

## 主要接口

### 认证

```text
POST   /api/auth/login
POST   /api/auth/logout
```

### 部门

```text
POST   /api/departments
GET    /api/departments
GET    /api/departments/{id}
PUT    /api/departments/{id}
DELETE /api/departments/{id}
```

### 用户

```text
POST   /api/users
GET    /api/users?pageNum=1&pageSize=10
GET    /api/users/{id}
PUT    /api/users/{id}
PUT    /api/users/{id}/password
DELETE /api/users/{id}
```

### 文件夹

```text
GET    /api/folders?parentId=0
POST   /api/folders
PUT    /api/folders/{id}
DELETE /api/folders/{id}

GET    /api/folders/trash
PUT    /api/folders/{id}/restore
```

### 文件

```text
POST   /api/files/upload?folderId={folderId}
GET    /api/files?folderId={folderId}&pageNum=1&pageSize=20
GET    /api/files/{id}/download
DELETE /api/files/{id}

GET    /api/files/trash
PUT    /api/files/{id}/restore
DELETE /api/files/{id}/permanent
```

除：

```text
POST /api/auth/login
```

之外，`/api/**` 下接口默认都需要 Bearer Token。

---

## 统一返回格式

普通 JSON 接口统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

常用状态码：

| code | 含义 |
| --- | --- |
| `200` | 成功 |
| `400` | 参数或业务请求错误 |
| `401` | 未登录、Token 失效或被异地登录踢下线 |
| `403` | 已登录但没有权限 |
| `404` | 资源不存在 |
| `409` | 数据冲突，例如并发修改 / 重名 / 配额冲突 |
| `500` | 服务端异常 |

文件下载接口直接返回文件流，不使用上述 JSON 包装。

---

## 文件存储

默认文件目录：

```text
./storage
```

可以通过：

```text
STORAGE_ROOT
```

修改。

上传流程包含：

```text
权限校验
→ 文件大小校验
→ MIME 检测
→ 配额占用
→ 写入磁盘
→ 计算 SHA-256
→ 写入数据库
→ 事务异常补偿
```

默认单文件限制：

```text
50 MB
```

Spring Multipart 与业务存储层均配置了 50 MB 限制。

---

## 测试

后端测试：

```bash
./mvnw test
```

当前项目包含的测试包括：

- `SessionKickoutTest`：第二次登录使第一次 Token 失效
- `FileMimeTypeServiceTest`：文件 MIME 检测
- `LocalStorageServiceImplSha256Test`：SHA-256 / 本地存储相关测试
- Spring Boot 上下文测试

---

## 前端页面

当前前端包含：

```text
/login        登录
/dashboard    首页
/drive        文件空间
/trash        回收站
/profile      个人资料
/users        用户管理
/departments  部门管理
```

菜单会根据当前用户角色动态显示。

---

## 当前未实现 / 可继续扩展

项目目前没有重点实现以下能力：

- 分片上传
- 断点续传
- 秒传
- 在线文件预览
- 文件分享链接
- 文件版本管理
- Redis
- 消息队列
- 操作审计日志
- 对象存储（MinIO / OSS / S3）
- 多实例部署下的分布式 Session 管理

当前 `current_session_id` 存在 MySQL 中，因此单实例或共享数据库场景可以正常工作；如果未来需要高并发、多实例和更高频的会话访问，可以再考虑 Redis。

---

## 开发注意事项

当前仓库中前后端位于同一项目根目录。

建议 Git 不要提交以下运行时或 IDE 文件：

```text
node_modules/
dist/
target/
storage/
.idea/
```

其中 `storage/` 是本地上传文件目录，通常不应该提交到代码仓库。

`docs/` 下部分文档属于项目早期学习阶段记录，其中部分“未实现”内容在当前版本已经完成，例如：

- 配额
- Apache Tika
- SHA-256
- 回收站
- 永久删除
- 文件清理补偿

以当前源码实现和本 README 为准。

---

## 项目定位

这是一个用于学习和实践的部门文件管理系统，重点覆盖：

```text
Spring Boot REST API
MyBatis-Plus
MySQL
JWT Authentication
RBAC / 业务权限
文件系统操作
数据库事务
文件事务补偿
配额并发控制
Vue 3 前后端联调
```

适合作为 Java Web / Spring Boot 综合项目继续扩展。

---

## License

当前项目未指定开源许可证。如准备公开发布到 GitHub，建议根据实际需求补充 `LICENSE`。
