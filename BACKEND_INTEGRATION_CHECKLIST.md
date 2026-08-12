# 后端对接检查清单

1. Spring Boot 后端运行在 `http://localhost:8082`。
2. 前端开发环境运行 `npm run dev`，Vite 会把 `/api` 代理到 `8082`。
3. 登录成功返回结构必须保持：
   - `data.token`
   - `data.userId`
   - `data.realName`
   - `data.role`
   - `data.departmentId`
4. `AuthInterceptor` 对 401 返回标准 HTTP 401。前端收到 401 会清空本地 Token 并跳到登录页。
5. 文件下载接口返回 Blob，不走普通 JSON 解包。
6. 上传字段必须仍叫 `file`，文件夹参数仍叫 `folderId`。
7. 用户更新 DTO 字段当前后端写的是 `realname`（小写 n），前端已经按这一字段提交。
8. 用户禁用使用 `DELETE /api/users/{id}`，不会通过普通编辑表单直接改为 DISABLED，以保证走你清 Session 的逻辑。
9. 后端最大上传为 50MB，前端也做了 50MB 的预检查。
