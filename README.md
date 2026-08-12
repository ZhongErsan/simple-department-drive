# Department Drive Frontend

这是根据 `simple-department-drive` 后端接口生成的 Vue 3 前端。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Element Plus
- Axios

## 已接入功能

- 登录 / 退出登录
- JWT Bearer Token
- 异地登录或 Token 失效后自动清理本地登录状态并跳回登录页
- 首页 / 当前用户信息 / 配额展示
- 文件空间：
  - 根空间浏览
  - 多级文件夹浏览
  - 新建文件夹
  - 重命名文件夹
  - 删除空文件夹
  - 文件上传（按后端限制提示 50MB）
  - 文件分页
  - 文件下载
  - 文件删除
- 回收站：
  - 文件恢复
  - 文件永久删除
  - 文件夹恢复
- 用户管理：
  - ADMIN：新增、编辑、禁用、启用、重置密码
  - MINISTER：查看本部门用户
- 部门管理：
  - ADMIN：新增、编辑、禁用
- 个人资料 / 用户配额
- ADMIN / MINISTER / MEMBER 前端菜单权限

## 与后端的对应关系

默认后端地址：`http://localhost:8082`

Vite 开发服务器通过 `vite.config.js` 将 `/api` 代理到后端，因此开发时无需修改 CORS 地址。

## 启动

先启动你的 Spring Boot 后端（8082），然后：

```bash
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

## 打包

```bash
npm run build
```

生成：

```text
dist/
```

生产环境如果前后端同域部署，可以让 Nginx：
- `/` 指向前端 `dist`
- `/api/` 反向代理到 Spring Boot `8082`

如果分开部署，请复制 `.env.production.example` 为 `.env.production` 并设置 `VITE_API_BASE_URL`。

## 重要说明

当前前端严格按本次上传后端中的接口实现，没有添加后端不存在的接口。

后端当前没有：
- 文件夹永久删除接口
- 单独的部门启用接口
- 文件重命名接口
- 文件移动接口
- 当前用户 `/me` 接口

所以前端也不会伪造这些功能。

