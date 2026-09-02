# Department Drive

一个面向部门协作场景的文件管理系统，提供
**部门公共区、投稿区、个人空间、角色权限、文件上传下载、回收站、容量配额、用户与部门管理**
等能力。

项目采用前后端分离设计。后端围绕文件系统中常见的
**权限、并发、一致性、逻辑删除、分页查询与物理文件生命周期**
做了较完整的工程化处理。

> 当前版本：V1.0

------------------------------------------------------------------------

## 项目亮点

- **三级角色权限模型**：支持
  `ADMIN / MINISTER / MEMBER`，并结合部门与文件区域进行细粒度权限控制。
- **三类文件空间**：部门公共区 `PUBLIC`、投稿区 `CONTRIBUTION`、个人空间
  `PERSONAL`。
- **JWT + Session ID 双重认证**：JWT 负责身份凭证，数据库保存当前有效
  Session ID，实现单账号新登录覆盖旧会话、禁用/改密后旧 Token 失效。
- **文件真实类型校验**：使用 Apache Tika 读取文件内容识别 MIME
  Type，并与扩展名白名单进行双重校验，避免仅依赖前端 `Content-Type`。
- **SHA-256 文件摘要**：文件落盘过程中计算
  SHA-256，用于文件完整性信息记录。
- **原子容量配额控制**：个人空间与部门共享空间分别维护配额，通过条件
  `UPDATE` 原子判断剩余容量，避免并发上传导致超额。
- **事务与行锁保护**：上传、恢复等关键流程通过 `SELECT ... FOR UPDATE`
  锁定目标目录，降低目录被并发删除等状态竞争风险。
- **安全的永久删除**：使用
  `DELETE ... WHERE id = ? AND status = 'DELETED'`，避免文件恢复与永久删除并发时误删已恢复记录。
- **回收站权限下推 SQL**：文件回收站通过 `JOIN drive_folder`
  在数据库层完成权限过滤并分页，避免先查询全量数据再在 Java 层过滤。
- **消除 N+1 查询**：文件列表与回收站批量收集 `uploaderId`，使用 `IN`
  查询一次获取上传人信息，再在内存中组装 VO。
- **数据库与磁盘一致性处理**：通过 Spring `TransactionSynchronization`
  将物理文件清理与事务提交/回滚生命周期关联。
- **孤儿文件兜底清理**：持久化清理任务 + 定时扫描 +
  指数退避重试，处理事务回滚、删除失败、异常宕机等场景产生的孤儿文件。
- **前后端分页与统一异常处理**：文件、用户、回收站等列表限制分页大小，后端统一业务异常响应，前端统一处理登录失效。

------------------------------------------------------------------------

## 功能概览

### 文件与文件夹

- 文件夹树形浏览
- 新建文件夹
- 文件夹重命名
- 文件上传与上传进度展示
- 文件下载
- 文件逻辑删除
- 文件/文件夹回收站
- 文件恢复
- 文件永久删除
- 同目录同名冲突处理
- 文件列表分页
- 回收站文件分页

### 权限与账号

- 用户登录 / 退出
- JWT Bearer Token 认证
- 单账号有效会话控制
- 用户禁用 / 启用
- 重置密码并使旧会话失效
- 用户分页管理
- 用户角色与部门调整
- 部门管理
- 个人资料与容量使用情况展示

### 存储与安全

- 单文件最大 50 MB
- 文件扩展名白名单
- Apache Tika MIME 真伪校验
- SHA-256 摘要计算
- 本地磁盘存储
- 个人空间 / 部门空间容量配额
- 事务回滚文件清理
- 永久删除后物理文件清理
- 孤儿文件定时扫描
- 清理失败持久化重试
- 指数退避控制重试频率

------------------------------------------------------------------------

## 权限模型

系统角色：

| 角色       | 说明                                                           |
|------------|----------------------------------------------------------------|
| `ADMIN`    | 系统管理员，可进行系统级用户、部门和文件管理                   |
| `MINISTER` | 部门部长，可管理本部门公共区、投稿区以及自己的个人空间         |
| `MEMBER`   | 普通成员，可访问本部门共享区域，并管理自己的个人空间和投稿文件 |

文件区域：

| 区域                  | `ADMIN`  | `MINISTER`   | `MEMBER`                         |
|-----------------------|----------|--------------|----------------------------------|
| `PUBLIC` 公共区       | 全部管理 | 本部门可管理 | 本部门可查看                     |
| `CONTRIBUTION` 投稿区 | 全部管理 | 本部门可管理 | 本部门可上传；删除自己上传的文件 |
| `PERSONAL` 个人空间   | 全部管理 | 仅自己的空间 | 仅自己的空间                     |

权限判断以后端为最终准则，前端路由和按钮权限只负责交互展示，不能替代服务端鉴权。

------------------------------------------------------------------------

## 核心流程

### 文件上传

``` mermaid
flowchart TD
    A[接收上传请求] --> B[校验用户与目录权限]
    B --> C[校验文件大小与文件名]
    C --> D[Apache Tika 检测真实 MIME]
    D --> E[检查同目录同名文件]
    E --> F[文件写入本地磁盘并计算 SHA-256]
    F --> G[注册事务回滚清理回调]
    G --> H[FOR UPDATE 锁定目标目录]
    H --> I[重新校验目录状态与权限]
    I --> J[原子占用个人/部门配额]
    J --> K[写入 drive_file 元数据]
    K --> L{事务结果}
    L -->|提交| M[保留物理文件]
    L -->|回滚| N[清理物理文件或登记重试任务]
```

上传流程在真正写入数据库前会重新锁定目标目录，避免文件落盘期间目录被其他请求删除后仍继续提交文件元数据。

### 永久删除

``` mermaid
flowchart TD
    A[读取回收站文件] --> B[校验删除权限]
    B --> C["DELETE WHERE id = ? AND status = 'DELETED'"]
    C --> D{affected == 1?}
    D -->|否| E[返回 409 状态冲突]
    D -->|是| F[提交数据库事务]
    F --> G[afterCommit 清理物理文件]
    G --> H{删除成功?}
    H -->|是| I[完成]
    H -->|否| J[写入持久化清理任务]
```

数据库删除动作本身再次要求文件仍处于 `DELETED`
状态，因此即使恢复请求与永久删除并发，也不会直接按主键误删已经恢复的文件。

### 孤儿文件清理

``` mermaid
flowchart LR
    A[事务回滚 / 删除失败 / 异常宕机] --> B[即时清理或登记任务]
    B --> C[storage_cleanup_task]
    C --> D[定时任务批量拉取]
    D --> E[再次检查 drive_file 是否仍引用]
    E -->|仍被引用| F[取消清理任务]
    E -->|无引用| G[删除物理文件]
    G -->|失败| H[指数退避后重试]
```

除了持久化任务，系统还会扫描超过安全时间窗口的磁盘文件，并批量核对数据库引用，作为异常宕机等极端情况下的兜底。

------------------------------------------------------------------------

## N+1 查询优化

文件列表需要展示上传人姓名。逐文件执行：

``` text
查询文件列表
→ 每个文件再 SELECT 一次 sys_user
```

会形成典型的 N+1 查询。

当前实现先收集当前页全部 `uploaderId`：

``` text
DriveFile Page
    ↓
收集并去重 uploaderId
    ↓
SELECT * FROM sys_user WHERE id IN (...)
    ↓
Map<userId, SysUser>
    ↓
内存组装 FileView / TrashFileView
```

因此上传人信息查询由最多 N 次数据库访问降为 1 次批量查询。

回收站权限过滤则采用另一种策略：由于 `drive_folder` 中的
`department_id / area_type / owner_id` 会直接决定文件是否可见，因此通过
`JOIN + WHERE`
将权限条件下推数据库，再执行分页，而不是把无权限数据全部查回 Java
后过滤。

------------------------------------------------------------------------

## 配额并发控制

个人空间与部门共享空间分别维护：

``` text
quota_bytes
used_bytes
```

上传时不采用“先 SELECT 剩余容量，再 UPDATE”的方式，而是使用条件更新：

``` sql
UPDATE ...
SET used_bytes = used_bytes + ?
WHERE ...
  AND used_bytes + ? <= quota_bytes;
```

通过受影响行数判断是否成功占用配额，使“检查容量 +
扣减容量”成为一个原子数据库操作，降低并发上传导致容量超卖的风险。

删除文件时释放对应配额；恢复文件时重新占用配额。

------------------------------------------------------------------------

## 技术栈

### 后端

- Java
- Spring Boot
- Spring MVC
- MyBatis-Plus
- MySQL
- JWT
- BCrypt
- Apache Tika
- Spring Transaction
- Spring Scheduling
- Lombok

### 前端

- Vue 3
- Vue Router
- Element Plus
- Axios
- Vite 环境变量

### 存储

- 本地文件系统
- MySQL 保存文件元数据、权限数据、容量数据与清理任务

------------------------------------------------------------------------

## 系统架构

``` mermaid
flowchart LR
    U[Browser] --> V[Vue 3 + Element Plus]
    V -->|Axios / Bearer Token| C[Spring MVC Controller]
    C --> A[AuthInterceptor]
    A --> S[Service Layer]
    S --> P[DrivePermissionService]
    S --> Q[QuotaService]
    S --> M[MyBatis-Plus Mapper]
    M --> DB[(MySQL)]
    S --> LS[LocalStorageService]
    LS --> FS[(Local File System)]
    S --> TS[TransactionSynchronization]
    TS --> OC[OrphanFileCleanupService]
    OC --> CT[(storage_cleanup_task)]
```

------------------------------------------------------------------------

## 主要数据模型

| 表                     | 作用                                                        |
|------------------------|-------------------------------------------------------------|
| `sys_user`             | 用户、角色、部门归属、个人配额、当前有效 Session            |
| `sys_department`       | 部门信息、部门共享空间配额                                  |
| `drive_folder`         | 文件夹树、区域类型、Owner、逻辑删除状态                     |
| `drive_file`           | 文件元数据、上传人、存储路径、大小、SHA-256、MIME、删除状态 |
| `storage_cleanup_task` | 物理文件清理失败后的持久化重试任务                          |

------------------------------------------------------------------------

## 项目结构

当前源码的主要结构如下：

``` text
src/
├── api/                         # 前端 API 封装
├── layout/                      # 前端主布局
├── router/                      # Vue Router 与角色路由控制
├── store/                       # 登录状态
├── styles/                      # 全局样式
├── utils/                       # HTTP、格式化工具
├── views/                       # 页面
│   ├── DashboardView.vue
│   ├── DriveView.vue
│   ├── TrashView.vue
│   ├── ProfileView.vue
│   ├── UsersView.vue
│   └── DepartmentsView.vue
│
├── main/java/com/easypan/
│   ├── auth/                    # JWT、登录拦截器、用户上下文
│   ├── common/                  # 统一响应
│   ├── config/                  # MVC、分页、密码、Tika、演示数据配置
│   ├── controller/              # REST API
│   ├── exception/               # 统一异常处理
│   ├── mapper/                  # MyBatis-Plus Mapper / 自定义 SQL
│   ├── model/                   # DTO / Entity / Enum / VO
│   ├── service/                 # 权限、文件、目录、配额、清理等核心业务
│   └── storage/                 # 本地存储抽象与实现
│
├── main/resources/
│   ├── application.yml
│   └── application.properties
│
└── test/java/com/easypan/
    ├── auth/
    ├── service/
    └── storage/
```

------------------------------------------------------------------------

## 后端配置

默认后端端口：

``` text
8082
```

`application.yml` 支持通过环境变量覆盖主要配置：

| 环境变量                           | 默认值 / 说明                  |
|------------------------------------|--------------------------------|
| `SERVER_PORT`                      | `8082`                         |
| `DB_URL`                           | MySQL `simple_drive` 数据库    |
| `DB_USERNAME`                      | `root`                         |
| `DB_PASSWORD`                      | `123`                          |
| `JWT_SECRET`                       | JWT 签名密钥，生产环境必须替换 |
| `JWT_EXPIRATION_SECONDS`           | `86400`                        |
| `DEMO_DATA_ENABLED`                | `true`                         |
| `STORAGE_ROOT`                     | `./storage`                    |
| `STORAGE_CLEANUP_ENABLED`          | `true`                         |
| `STORAGE_ORPHAN_MIN_AGE`           | `PT1H`                         |
| `STORAGE_CLEANUP_BATCH_SIZE`       | `100`                          |
| `STORAGE_CLEANUP_RETRY_BASE_DELAY` | `PT1M`                         |
| `STORAGE_CLEANUP_RETRY_MAX_DELAY`  | `PT1H`                         |

前端 API 地址通过：

``` text
VITE_API_BASE_URL
```

配置；未指定时默认使用：

``` text
/api
```

> 生产部署时请替换数据库密码、JWT Secret，并将 CORS
> 来源限制为实际前端域名。

------------------------------------------------------------------------

## 演示账号

当：

``` text
DEMO_DATA_ENABLED=true
```

时，后端启动会幂等初始化演示数据，包括技术部、共享根目录、个人空间以及测试账号。

| 角色       | 用户名     | 密码       |
|------------|------------|------------|
| 系统管理员 | `admin`    | `admin123` |
| 部门部长   | `minister` | `123456`   |
| 普通成员   | `member`   | `123456`   |

此外还会初始化若干部长与普通成员账号，用于用户列表和权限分页测试。

> 演示账号仅用于本地开发和功能验证，部署公开环境时建议关闭
> `DEMO_DATA_ENABLED`。

------------------------------------------------------------------------

## 文件上传限制

当前允许的主要文件扩展名：

``` text
jpg / jpeg / png / gif
pdf
doc / docx
xls / xlsx
ppt / pptx
txt / md
zip
```

系统不会直接信任浏览器上传的 `Content-Type`，而是使用 Apache Tika
检测文件真实内容类型，并校验真实 MIME 与扩展名是否匹配。

单文件默认最大：

``` text
50 MB
```

------------------------------------------------------------------------

## 接口概览

### 认证

``` text
POST   /api/auth/login
POST   /api/auth/logout
```

### 文件

``` text
GET    /api/files
POST   /api/files/upload
GET    /api/files/{id}/download
DELETE /api/files/{id}
GET    /api/files/trash
PUT    /api/files/{id}/restore
DELETE /api/files/{id}/permanent
```

### 文件夹

``` text
GET    /api/folders
POST   /api/folders
PUT    /api/folders/{id}
DELETE /api/folders/{id}
GET    /api/folders/trash
PUT    /api/folders/{id}/restore
```

### 用户

``` text
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
PUT    /api/users/{id}/password
DELETE /api/users/{id}
PUT    /api/users/{id}/enable
```

### 部门

``` text
GET    /api/departments
GET    /api/departments/{id}
POST   /api/departments
PUT    /api/departments/{id}
DELETE /api/departments/{id}
```
------------------------------------------------------------------------

## 测试

源码中已经包含以下测试方向：

- 登录 Session 踢下线场景
- 文件 MIME 类型检测
- 本地存储 SHA-256 计算
- Spring Boot 上下文加载

建议后续继续补充：

- 上传与删除的事务一致性测试
- 文件恢复 / 永久删除并发测试
- 配额并发扣减测试
- 权限矩阵集成测试
- 孤儿文件清理与重试测试

------------------------------------------------------------------------

## 后续优化方向

当前版本已经覆盖完整业务闭环，后续可根据实际数据规模继续演进：

- 使用 `EXPLAIN ANALYZE` 根据真实查询计划优化文件列表与回收站联合索引
- 进一步缩短上传流程中的数据库事务范围
- 文件夹回收站增加分页与 SQL 权限下推
- 对象存储适配（MinIO / OSS / S3）
- 增加操作审计日志
- 增加文件预览、搜索与版本管理
- 增加自动化集成测试与 CI

------------------------------------------------------------------------

## 设计原则

这个项目重点关注的不是“堆功能”，而是文件系统业务中几个容易被忽略的问题：

``` text
权限是否以后端为准？
并发上传会不会超配额？
上传失败会不会留下孤儿文件？
数据库提交失败后磁盘文件怎么办？
永久删除和恢复并发会不会误删？
回收站数据量变大后是否还能分页？
列表关联用户信息会不会产生 N+1？
用户被禁用、改密或异地登录后旧 Token 是否还能继续使用？
```

当前实现围绕这些问题给出了对应的工程化处理，并保留了进一步扩展到对象存储、审计、搜索等能力的空间。
