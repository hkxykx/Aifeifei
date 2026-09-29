<div align="center">

# Fly

**きらめく — 像星光一样闪烁**

一个从零搭建的全栈个人博客系统：Next.js 16 前台 + Spring Boot 3 后台 + Vue 3 管理端，
内置小说阅读器、百草园工具箱、Live2D 看板娘，Docker 一键部署。

![Next.js](https://img.shields.io/badge/Next.js-16-black?logo=next.js)
![React](https://img.shields.io/badge/React-19-61dafb?logo=react)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178c6?logo=typescript)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4-06b6d4?logo=tailwindcss)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6db33f?logo=springboot)
![JDK](https://img.shields.io/badge/JDK-21-blue?logo=openjdk)
![Vue](https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker_Compose-ready-2496ED?logo=docker)
![License](https://img.shields.io/badge/License-MIT-blue)

</div>

---

## 效果展示

<div align="center">
  <img src="docs/images/首页.png" width="90%" />
  <img src="docs/images/文章.png" width="90%" />
  <img src="docs/images/说说.png" width="90%" />
  <img src="docs/images/照片墙.png" width="90%" />
  <img src="docs/images/归档.png" width="90%" />
</div>

---

## ✨ 功能总览

### 博客前台

| 模块 | 路径 | 描述 |
|:-----|:-----|:-----|
| 首页 | `/` | 文章预览、说说、照片墙一站式入口 |
| 文章 | `/posts` | 分类筛选、标签、Markdown 渲染、代码高亮 |
| 说说 | `/moments` | 碎片化记录，类朋友圈时间线 |
| 留言 | `/messages` | 轻量话题讨论区 |
| 小说 | `/novel` | 书架 → 搜索 → 目录 → 阅读的完整闭环 |
| 百草园 | `/garden` | 17+ 个交互式小工具与可视化实验（见下） |
| 收藏夹 | `/bookmark` | 站点导航，分类管理，自动获取 favicon |
| 照片墙 | `/photowall` | 相册瀑布流展示 |
| 友链 | `/friends` | 漂流瓶主题，可拖动交互 |
| 归档 | `/timeline` | 时间河流可视化，拖动浏览全部文章 |
| 音乐 | `/music` | 云音乐歌单（Meting）+ 本地音乐库扫描导入 |
| 项目 | `/projects` | 个人项目展示，GitHub/Gitee 链接 |
| 关于 | `/about` | Markdown 书写的个人介绍 |
| RSS | `/feed` | 全站订阅源 |

### 百草园 `/garden`

首页是站点数据看板（运行天数、发文趋势图、分类占比），往下滑是各种即开即玩的小工具：

- **3D 太阳系**（`stars` / `solar`）— three.js 驱动，行星数据与轨道模拟
- **排序算法可视化** — 快排、归并、堆排等 8 种算法动画对比
- **康威生命游戏** — 内置滑翔机等经典图案
- **流体模拟**（Jos Stam 网格法）、流沙、烟花、雨 — 各类物理/粒子小实验
- **函数绘图** — 输入表达式即时绘制图像与导数
- **Markdown 编辑器** — 编辑 / 分栏 / 预览三种模式
- **Python / JSON 工具** — CodeMirror 6 编辑器
- **二维码生成器、配色板、城市地图（Leaflet）、万花筒画板、小屋设计器、访客环境检测** …

### 管理后台

- **Next.js 端 `/admin`**（随 Nginx 对外暴露）：管理员登录、留言/说说审核、小说书架管理、站点配置
- **Vue 3 端**（vue-pure-admin，随 backend 镜像分发）：文章、分类、标签、评论、说说、相册、项目、友链、收藏夹全量 CRUD，图片压缩上传至阿里云 OSS；仅限 `127.0.0.1:8000/admin` 直连访问，不经 Nginx 暴露
- **访客统计**：后端记录访问 IP/UA/归属地（含代理识别），后台仪表盘聚合展示

## 技术栈

<table>
<tr>
<td width="50%" valign="top">

**前端**
- **Next.js 16** + **React 19** — App Router，SSR/SSG
- **Tailwind CSS 4** — 原子化样式
- **Framer Motion** — 页面过渡与微交互
- **three.js** + **@react-three/fiber** — 3D 场景
- **CodeMirror 6** — 代码编辑器
- **pixi-live2d-display** — 看板娘
- **Leaflet / recharts / dnd-kit** — 地图、图表、拖拽

</td>
<td width="50%" valign="top">

**后端**
- **Spring Boot 3.3** + **JDK 21**
- **Spring Data JPA** + **PostgreSQL 16**
- **JWT**（jjwt）— 无状态认证
- **阿里云 OSS** — 可选，图片对象存储
- **reader3 协议** — 内置小说搜索（SSE 流式）/目录/正文解析与清洗

</td>
</tr>
<tr>
<td width="50%" valign="top">

**管理后台**
- **Vue 3** + **Element Plus**（vue-pure-admin 模板）
- 构建产物内嵌于后端，无需单独部署

</td>
<td width="50%" valign="top">

**部署**
- **Docker Compose** 一键编排四服务
- **Nginx** — HTTPS 终结、按 IP 限流、登录防爆破
- **acme.sh** — 证书自动续签

</td>
</tr>
</table>

## 项目结构

```
.
├── fly/                        # 前端（Next.js App Router）
│   ├── app/                        # 页面路由
│   │   ├── posts/                  #   文章系统
│   │   ├── novel/                  #   小说阅读（书架 → 搜索 → 目录 → 阅读）
│   │   ├── garden/                 #   百草园（17+ 交互式小工具）
│   │   ├── moments/ messages/      #   说说 / 留言
│   │   ├── bookmark/ photowall/    #   收藏夹 / 照片墙
│   │   ├── friends/ timeline/      #   友链 / 归档
│   │   ├── music/ projects/ about/ #   音乐 / 项目 / 关于
│   │   ├── admin/                  #   管理后台（登录、审核、书架、站点配置）
│   │   ├── feed/                   #   RSS 订阅
│   │   └── api/                    #   API 封装 + Meting 音乐路由
│   ├── components/                 # UI 组件（layout / ui / widgets / music …）
│   ├── content/posts/              # 本地 Markdown 文章
│   ├── data/                       # 站点初始数据（说说 / 照片 / 专辑 …）
│   ├── public/                     # 静态资源（图片 / Live2D 模型）
│   └── siteConfig.ts               # 站点全局配置
│
├── fly-backend/                # 后端（Spring Boot 3 / JDK 21）
│   ├── src/main/java/com/fly/
│   │   ├── controller/             # RESTful API（文章/说说/相册/小说/音乐/访客 …）
│   │   ├── service/                # 业务逻辑层
│   │   ├── repository/             # Spring Data JPA
│   │   ├── entity/                 # JPA 实体
│   │   ├── security/               # JWT 认证与令牌服务
│   │   └── config/                 # CORS、OSS、Web 配置
│   ├── admin/                      # 管理后台（Vue 3 + Element Plus）
│   ├── init_db.sql                 # 建表脚本（Docker 首次启动自动执行）
│   └── init_admin.sh               # 管理员账号初始化
│
├── deploy/                     # Nginx 配置与 HTTPS 证书目录（证书不入库）
├── docs/                       # 部署文档、运维手册、README 截图
└── docker-compose.yml          # 一键编排：PostgreSQL + 后端 + 前端 + Nginx
```

## 🚀 快速开始

### Docker 部署（推荐）

```bash
git clone https://github.com/hkxykx/Aifeifei.git
cd Aifeifei

cp .env.example .env
# 编辑 .env：
#   FLY_SECRET_KEY          必填，≥32 字符（openssl rand -hex 32）
#   POSTGRES_PASSWORD       建议修改
#   ADMIN_INITIAL_PASSWORD  建议显式设置强管理员密码

docker compose up -d --build
```

启动后：

- 博客前台：<http://localhost/>
- 管理后台：<http://localhost/admin>（管理员用户名 `feifei`）

> HTTPS：将证书 `fullchain.pem` / `privkey.pem` 放入 `deploy/certs/`，
> 并按域名修改 `deploy/nginx.conf` 的 `server_name`，详见
> [docs/Docker部署.md](docs/Docker部署.md)。

### 本地开发

```bash
# 前端（http://localhost:3000）
cd fly
pnpm install
pnpm dev

# 后端（http://localhost:8000，需本地 PostgreSQL，先执行 fly-backend/init_db.sql）
cd fly-backend
mvn spring-boot:run
```

后端环境变量（`FLY_SECRET_KEY` 等）参考 `fly-backend/.env.example`，
完整步骤见 [docs/传统部署.md](docs/传统部署.md)。

### 生产部署

从一台干净的 Rocky Linux 9.8 服务器从零安装到上线验收——Docker 安装、防火墙、
备份恢复、升级回滚、证书自动续签、故障处理——见 [docs/运维手册.md](docs/运维手册.md)。

## 环境变量

Docker 部署使用仓库根目录 `.env`（参考 [.env.example](.env.example)）；
传统部署使用 `fly-backend/.env`（参考 [fly-backend/.env.example](fly-backend/.env.example)）。

| 变量 | 必填 | 默认 | 说明 |
|:---|:---:|:---|:---|
| `FLY_SECRET_KEY` | ✅ | — | JWT 签名密钥，≥32 字符 |
| `POSTGRES_PASSWORD` | 建议 | `postgres` | 数据库密码 |
| `ADMIN_INITIAL_PASSWORD` | ✅ | — | 管理员初始密码（仅数据库首次初始化生效；未设置时启动失败） |
| `FLY_CORS_ORIGINS` | ✅ | — | **必含线上域名**（浏览器同源 POST 也携带 Origin 头，缺失则所有 POST 返回 403） |
| `FLY_OSS_*` | — | 空 | 不配置则图片存本地 `uploads/` 目录 |
| `MUSIC_DIR_120` / `MUSIC_DIR_MP3` | — | `./data/music/*` | 本地音乐目录（只读挂载，后台"扫描"导入） |
| `NEXT_PUBLIC_API_URL` | — | `http://backend:8000` | 容器网络内地址，一般保持默认 |

## 🔒 安全设计

- JWT 无状态认证，密钥缺失时后端拒绝启动
- Nginx 统一限流：通用 API 30 r/s、登录 1 r/s 防爆破、音乐/小说源独立配额
- `X-Forwarded-For` 由 Nginx 覆写，杜绝客户端伪造来源 IP
- 上传文件类型白名单 + `nosniff` 防 MIME 嗅探
- Vue 管理端不对外暴露，仅容器内网/本机直连可达
- HTTPS 证书与私钥仅存服务器 `deploy/certs/`，不入代码仓库

## 设计亮点

- **Glassmorphism 风格** — 全站毛玻璃质感，亮色暗色双主题
- **微交互动画** — Framer Motion 驱动：页面过渡、卡片悬停、果冻弹跳
- **小说阅读系统** — 四级路由架构，SSE 流式搜索，阅读进度/设置持久化，正文服务端清洗无广告
- **百草园** — 把平时写的可视化小 demo 收进一个"工具箱"页面，随写随加
- **Live2D 看板娘** — 右下角可互动，连续点击 Logo 7 次触发彩蛋
- **漂流瓶友链** — 可拖动的漂浮瓶子，点击查看详情
- **时间河流** — 归档页可拖动的时间线，纵览全部文章
- **移动端适配** — 响应式布局，移动端抽屉导航

## License

[MIT](LICENSE) © 2026 孤鸿
