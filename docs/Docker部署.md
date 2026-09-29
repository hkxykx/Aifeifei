# Fly Docker 部署

一条命令拉起全栈：PostgreSQL + Java 后端 + Next.js 前端（含 /admin 管理后台）+ Nginx。

## 架构

| 服务 | 镜像/构建 | 端口 | 说明 |
|:-----|:----------|:-----|:-----|
| `db` | `postgres:16-alpine` | 5432（仅容器网） | 首次启动自动执行 `init_db.sql` 建表 + 种子数据，再由 `init_admin.sh` 创建管理员 |
| `backend` | `fly-backend/Dockerfile` | 127.0.0.1:8000（本机调试） | Spring Boot；三阶段构建：admin（Vue）→ jar → JRE |
| `frontend` | `fly/Dockerfile` | 127.0.0.1:3000（本机调试） | Next.js 生产模式 |
| `nginx` | `nginx:alpine` | **80（正式入口）** | 规则见 `deploy/nginx.conf` |

```
浏览器 → nginx:80 ─┬─ /api/music、/api/uapis（前端路由） → frontend:3000
                   ├─ /api/、/uploads/         → backend:8000
                   ├─ /reader3/（小说阅读）     → backend:8000
                   ├─ /admin（管理后台）        → frontend:3000
                   └─ /                         → frontend:3000
```

## 前置条件

- Docker 20+ 与 Docker Compose v2（`docker compose version` 可用）
- 全新 Rocky Linux 9.8 / 10.x 服务器的系统初始化（安装 Docker、镜像加速、防火墙、SELinux）按 `docs/运维手册.md` 第 3 节执行

## 部署步骤

```bash
# 1. 准备配置
cp .env.example .env

# 2. 编辑 .env，至少填写 FLY_SECRET_KEY（必填，至少 32 字符）
#    生成方式：
echo "FLY_SECRET_KEY=$(openssl rand -hex 32)" >> .env

# 3. 构建并启动
docker compose up -d --build

# 4. 验证
curl http://localhost/api/health     # 期望 {"status":"ok"}
```

访问入口：

- 博客前台：`http://localhost/`
- 管理后台：`http://localhost/admin`（Next.js 页面，用户名 `feifei`；密码为 `.env` 中
  `ADMIN_INITIAL_PASSWORD`（必填，脚本无默认值；日志不打印密码），登录后请立即修改。
  忘记密码时直接在数据库重置，见 `docs/运维手册.md`「重置管理员密码」）
- API 调试：`http://localhost:8000`（绕过 Nginx）

## 常用命令

```bash
docker compose ps                  # 查看状态（db 应为 healthy）
docker compose logs -f backend     # 跟踪后端日志
docker compose up -d --build       # 代码更新后重新构建启动
docker compose down                # 停止（数据卷保留）
docker compose down -v             # 停止并删除数据库数据
```

## 配置说明（.env）

| 变量 | 必填 | 说明 |
|:-----|:----:|:-----|
| `FLY_SECRET_KEY` | ✅ | JWT 签名密钥，<32 字符时后端拒绝启动 |
| `POSTGRES_PASSWORD` | 建议 | 数据库密码（默认 `postgres`） |
| `ADMIN_INITIAL_PASSWORD` | ✅ | 管理员初始密码，仅首次初始化生效；未设置时 compose 拒绝启动 |
| `NEXT_PUBLIC_API_URL` | 默认 | SSR 首页数据抓取地址，容器网络内保持 `http://backend:8000` |
| `FLY_OSS_*` | — | 不配置则图片保存到本地 `./fly-backend/uploads/`（经 `/uploads/` 访问） |
| `FLY_CORS_ORIGINS` | ✅ | CORS 允许来源，**必含线上域名**（浏览器同源 POST 也携带 Origin 头，缺失则所有 POST 返回 403） |

注意：

- `NEXT_PUBLIC_API_URL` 是**构建参数**，修改后需 `docker compose build frontend` 生效。
- 管理后台页面（`fly/app/admin/`）的改动包含在 frontend 镜像中，需 `docker compose build frontend`。
- 后端 `fly-backend/admin/`（Vue 后台）仅随 backend 镜像分发，`/admin` 已不再转发到后端，改动它不影响线上后台。

## 数据持久化与备份

```bash
# 数据库数据存于命名卷 pgdata；上传文件存于 ./fly-backend/uploads
docker compose exec db pg_dump -U postgres fly > backup.sql   # 备份
docker compose exec -T db psql -U postgres fly < backup.sql   # 恢复
```

`init_db.sql` 仅在数据卷**首次创建**时执行，之后的结构变更需手动导入。

## 小说阅读服务（内置）

后端内置 reader3 协议接口（`/reader3/`），Nginx 直连 backend（SSE 关闭缓冲）。
数据源为 biquga.com 免费小说站：搜索/目录/正文在服务端解析，正文经 base64 解码并清洗（无广告、无付费章）。
书架默认为空，由用户在搜索结果/书籍详情页手动添加，可随时删除；书架存 `novel_bookshelf`，匿名阅读进度存 `novel_progress`。

小说模块**无账号体系**（注册/登录已全部关闭，全站仅保留默认管理员账户）：
阅读进度标签（章节 + 页码 + 是否看完）统一存 `novel_progress`，全局共享、匿名可用，
读过的书自动排在书架最前；老库若残留 `novel_user`、`novel_user_progress` 表可直接
`DROP TABLE`（新版代码不再读写）。

## 迁移到新服务器

镜像名已固定（`fly-frontend:1.0.1` / `fly-backend:1.0.1`），
`docker save` → `docker load` 后 `docker compose up -d`（**不带 --build**）即可直接跑起来。
完整步骤（离线/在线两条路径、数据迁移、迁移后验收）见 `docs/运维手册.md` 第 13 节附录 C。

## 可选：HTTPS

在云主机安全组放行 80/443 后，于 `deploy/nginx.conf` 的 `server` 前新增 443
配置（证书路径自行指定），并将 80 端口重定向到 443 即可；应用层无需改动。
