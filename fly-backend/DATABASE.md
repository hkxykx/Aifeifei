# Fly 数据库设计文档

> 数据库：PostgreSQL 16+
> 建表脚本：`init_db.sql`
> 执行方式：`psql -U postgres -d fly -f init_db.sql`

## 概览

共 19 张表，覆盖博客全部功能：文章、分类、标签、评论（GitHub 登录）、留言板、说说、相册、项目展示、友链、收藏夹、访客统计、本地音乐、站点配置。

```
User (独立, 管理员; 由 init_admin.sh 创建)
GitHubUser (GitHub 身份, 评论/留言作者) ──┬── Comment
                                        ├── Message
                                        └── ChatterComment
Post ──┬── Category (多对一)
       ├── PostTag ── Tag (多对多)
       └── Comment (一对多, 自引用回复)
Chatter ── ChatterComment (一对多)
Album ── Photo (一对多)
BookmarkCategory ── BookmarkSite (一对多)
Project (独立)
FriendLink (独立)
Message (独立)
SiteConfig (独立)
Visitor (独立, 访客统计)
Music (独立, 本地音乐)
```

---

## 1. User（用户/管理员）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 登录用户名 |
| hashed_password | VARCHAR(128) | NOT NULL | bcrypt 加密密码 |
| nickname | VARCHAR(50) | DEFAULT '' | 显示昵称 |
| avatar | VARCHAR(500) | DEFAULT '' | 头像 URL |
| email | VARCHAR(100) | DEFAULT '' | 邮箱 |
| bio | VARCHAR(500) | DEFAULT '' | 个人简介 |
| is_admin | BOOLEAN | DEFAULT FALSE | 是否管理员 |
| created_at | TIMESTAMP | DEFAULT NOW | 注册时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 2. Post（文章）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| title | VARCHAR(200) | NOT NULL | 标题 |
| slug | VARCHAR(200) | UNIQUE, NOT NULL, INDEX | URL 别名 |
| description | VARCHAR(500) | DEFAULT '' | 摘要 |
| content | TEXT | DEFAULT '' | 正文（Markdown） |
| cover | VARCHAR(500) | DEFAULT '' | 封面图 URL |
| category_id | INTEGER | FK→Category.id, NULLABLE | 所属分类 |
| status | VARCHAR(20) | DEFAULT 'draft', INDEX | draft / published / archived |
| is_pinned | BOOLEAN | DEFAULT FALSE | 是否置顶 |
| views | INTEGER | DEFAULT 0 | 浏览量 |
| likes | INTEGER | DEFAULT 0 | 点赞数 |
| word_count | INTEGER | DEFAULT 0 | 字数 |
| reading_time | INTEGER | DEFAULT 0 | 预计阅读时间（分钟） |
| published_at | TIMESTAMP | NULLABLE | 发布时间 |
| created_at | TIMESTAMP | DEFAULT NOW | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

**外键关系：**
- `category_id` → `Category.id`（多对一）

---

## 3. Category（分类）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| name | VARCHAR(50) | UNIQUE, NOT NULL | 分类名 |
| slug | VARCHAR(50) | UNIQUE, NOT NULL | URL 别名 |
| description | VARCHAR(200) | DEFAULT '' | 描述 |
| sort | INTEGER | DEFAULT 0 | 排序（小的在前） |
| post_count | INTEGER | DEFAULT 0 | 文章计数（冗余字段，加速查询） |
| created_at | TIMESTAMP | DEFAULT NOW | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 4. Tag（标签）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| name | VARCHAR(50) | UNIQUE, NOT NULL | 标签名 |
| slug | VARCHAR(50) | UNIQUE, NOT NULL | URL 别名 |
| post_count | INTEGER | DEFAULT 0 | 文章计数 |

---

## 5. PostTag（文章-标签 多对多中间表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| post_id | INTEGER | FK→Post.id, NOT NULL | 文章 ID |
| tag_id | INTEGER | FK→Tag.id, NOT NULL | 标签 ID |
| — | — | PRIMARY KEY (post_id, tag_id) | 联合主键 |

---

## 6. Comment（文章评论 — GitHub 登录）

访客对文章的评论，支持楼中楼回复（parent_id 自引用）；评论者身份为 GitHub 登录用户。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| post_id | INTEGER | FK→Post.id, NOT NULL | 所属文章 |
| parent_id | INTEGER | FK→Comment.id, NULLABLE | 父评论 ID（NULL 表示顶级评论） |
| github_user_id | INTEGER | FK→GitHubUser.id, NULLABLE | 评论者（GitHub 用户） |
| content | TEXT | NOT NULL | 评论内容 |
| likes | INTEGER | DEFAULT 0 | 点赞数 |
| ip | VARCHAR(45) | DEFAULT '' | IP 地址（防刷、审核用） |
| status | VARCHAR(20) | DEFAULT 'approved', INDEX | pending / approved / rejected（建表默认 approved，pending 由代码显式写入） |
| created_at | TIMESTAMP | DEFAULT NOW | 评论时间 |

---

## 7. Message（留言板/杂谈）

全站留言板，支持楼中楼回复（parent_id 自引用）；留言者身份为 GitHub 登录用户。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| github_user_id | INTEGER | FK→GitHubUser.id, NULLABLE | 留言者（GitHub 用户） |
| parent_id | INTEGER | FK→Message.id, NULLABLE | 父留言 ID（楼中楼回复） |
| content | TEXT | NOT NULL | 留言内容 |
| ip | VARCHAR(45) | DEFAULT '' | IP 地址 |
| status | VARCHAR(20) | DEFAULT 'approved', INDEX | pending / approved / rejected（建表默认 approved，pending 由代码显式写入） |
| likes | INTEGER | DEFAULT 0 | 点赞数 |
| created_at | TIMESTAMP | DEFAULT NOW | 留言时间 |

---

## 8. Chatter（说说/微语）

博主发布的心情短文，类似朋友圈/Twitter。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| content | TEXT | NOT NULL | 正文（Markdown） |
| images | TEXT | DEFAULT '[]' | 图片 URL 列表（JSON 数组） |
| mood | TEXT | DEFAULT '' | 心情/状态文本 |
| likes | INTEGER | DEFAULT 0 | 点赞数 |
| comments_count | INTEGER | DEFAULT 0 | 评论计数 |
| status | VARCHAR(20) | DEFAULT 'draft', INDEX | draft / published |
| created_at | TIMESTAMP | DEFAULT NOW | 发布时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 9. ChatterComment（说说评论 — GitHub 登录）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| chatter_id | INTEGER | FK→Chatter.id, NOT NULL | 所属说说 |
| parent_id | INTEGER | FK→ChatterComment.id, NULLABLE | 父评论 |
| github_user_id | INTEGER | FK→GitHubUser.id, NULLABLE | 评论者（GitHub 用户） |
| content | TEXT | NOT NULL | 评论内容 |
| ip | VARCHAR(45) | DEFAULT '' | IP 地址 |
| likes | INTEGER | DEFAULT 0 | 点赞数 |
| status | VARCHAR(20) | DEFAULT 'approved', INDEX | pending / approved / rejected（建表默认 approved，pending 由代码显式写入） |
| created_at | TIMESTAMP | DEFAULT NOW | 评论时间 |

---

## 10. Album（相册）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| title | VARCHAR(100) | NOT NULL | 相册标题 |
| description | VARCHAR(500) | DEFAULT '' | 描述 |
| cover | VARCHAR(500) | DEFAULT '' | 封面图 URL |
| photo_count | INTEGER | DEFAULT 0 | 照片计数 |
| sort | INTEGER | DEFAULT 0 | 排序 |
| created_at | TIMESTAMP | DEFAULT NOW | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 11. Photo（照片）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| album_id | INTEGER | FK→Album.id, NOT NULL | 所属相册 |
| url | VARCHAR(500) | NOT NULL | 图片 URL |
| caption | VARCHAR(200) | DEFAULT '' | 图片说明 |
| orientation | VARCHAR(20) | DEFAULT 'landscape' | 图片方向（landscape / portrait） |
| sort | INTEGER | DEFAULT 0 | 排序 |
| created_at | TIMESTAMP | DEFAULT NOW | 上传时间 |

---

## 12. Project（项目展示）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| name | VARCHAR(100) | NOT NULL | 项目名 |
| slug | VARCHAR(100) | UNIQUE, NOT NULL | URL 别名 |
| description | VARCHAR(500) | DEFAULT '' | 简短描述 |
| long_description | TEXT | DEFAULT '' | 详细介绍（Markdown） |
| cover_image | VARCHAR(500) | DEFAULT '' | 封面图 |
| tech_stack | TEXT | DEFAULT '[]' | 技术栈（JSON 数组） |
| link_github | VARCHAR(300) | DEFAULT '' | GitHub 地址 |
| link_gitee | VARCHAR(300) | DEFAULT '' | Gitee 地址 |
| link_live | VARCHAR(300) | DEFAULT '' | 在线演示地址 |
| link_docs | VARCHAR(300) | DEFAULT '' | 文档地址 |
| status | VARCHAR(20) | DEFAULT 'developing' | active / archived / developing |
| status_label | VARCHAR(20) | DEFAULT '' | 状态标签（维护中/开发中/已上线） |
| is_featured | BOOLEAN | DEFAULT FALSE | 是否推荐 |
| sort | INTEGER | DEFAULT 0 | 排序 |
| created_at | TIMESTAMP | DEFAULT NOW | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 13. FriendLink（友情链接）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| name | VARCHAR(100) | NOT NULL | 站点名称 |
| url | VARCHAR(300) | NOT NULL | 站点地址 |
| avatar | VARCHAR(500) | DEFAULT '' | 头像 / Logo |
| description | VARCHAR(300) | DEFAULT '' | 简介 |
| sort | INTEGER | DEFAULT 0 | 排序 |
| is_approved | BOOLEAN | DEFAULT FALSE | 是否已审核 |
| created_at | TIMESTAMP | DEFAULT NOW | 申请时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 14. SiteConfig（站点配置）

key-value 存储，存放博客全局配置（站点标题、社交链接、弹幕列表等）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| key | VARCHAR(100) | UNIQUE, NOT NULL | 配置键名 |
| value | TEXT | DEFAULT '' | 配置值（JSON 字符串） |
| description | VARCHAR(200) | DEFAULT '' | 配置说明 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 15. GitHubUser（GitHub 身份）

评论/留言/说说评论的访客身份（GitHub 登录态解析出的用户信息）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| github_id | INTEGER | UNIQUE, NOT NULL, INDEX | GitHub 用户 ID |
| login | VARCHAR(100) | NOT NULL | GitHub 用户名 |
| avatar | VARCHAR(500) | DEFAULT '' | 头像 URL |
| bio | VARCHAR(500) | DEFAULT '' | 个人简介 |
| created_at | TIMESTAMP | DEFAULT NOW | 首次出现时间 |

---

## 16. BookmarkCategory（收藏夹分类）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| name | VARCHAR(50) | NOT NULL | 分类名 |
| icon | VARCHAR(50) | DEFAULT '' | 图标 |
| description | VARCHAR(200) | DEFAULT '' | 描述 |
| sort | INTEGER | DEFAULT 0 | 排序 |
| created_at | TIMESTAMP | DEFAULT NOW | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

---

## 17. BookmarkSite（收藏站点）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| category_id | INTEGER | FK→BookmarkCategory.id, NOT NULL, INDEX | 所属分类 |
| name | VARCHAR(100) | NOT NULL | 站点名 |
| url | VARCHAR(300) | NOT NULL | 站点地址 |
| icon | VARCHAR(500) | DEFAULT '' | 图标 URL |
| description | VARCHAR(300) | DEFAULT '' | 简介 |
| platforms | TEXT | DEFAULT '[]' | 适用平台（JSON 数组） |
| sort | INTEGER | DEFAULT 0 | 排序 |
| created_at | TIMESTAMP | DEFAULT NOW | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW | 更新时间 |

**外键关系：**
- `category_id` → `BookmarkCategory.id`（多对一）

---

## 18. Visitor（访客记录）

每个 IP 一条（`ip` 唯一索引），由 `POST /api/visitors/record` 记录并附地理信息。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| ip | VARCHAR(45) | UNIQUE, NOT NULL | 访客 IP（唯一） |
| path | VARCHAR(500) | DEFAULT '' | 首次落地路径 |
| user_agent | TEXT | DEFAULT '' | 浏览器 UA |
| city / region / country / district | VARCHAR(100) | DEFAULT '' | 地理位置（IP 库解析） |
| org | VARCHAR(200) | DEFAULT '' | 运营商/组织 |
| asn | VARCHAR(50) | DEFAULT '' | ASN |
| is_mobile / is_proxy / is_hosting | BOOLEAN | DEFAULT FALSE | 访客属性标记 |
| browser / os | VARCHAR(50) | DEFAULT '' | 浏览器与操作系统 |
| device_type | VARCHAR(20) | DEFAULT '' | 设备类型 |
| created_at | TIMESTAMP | DEFAULT NOW, INDEX(DESC) | 首次访问时间 |

---

## 19. Music（本地音乐，管理后台上传）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | SERIAL | PK | 自增 ID |
| title | VARCHAR(255) | NOT NULL | 歌曲名 |
| artist | VARCHAR(255) | DEFAULT '未知歌手' | 歌手 |
| album | VARCHAR(255) | DEFAULT '' | 专辑 |
| cover | VARCHAR(500) | DEFAULT '' | 封面 URL |
| url | VARCHAR(1000) | NOT NULL | 播放地址 |
| path | VARCHAR(1000) | DEFAULT '' | 服务器文件路径 |
| source | VARCHAR(20) | DEFAULT 'upload' | 来源（upload / scan） |
| lrc | TEXT | DEFAULT '' | 歌词（LRC） |
| duration | DOUBLE PRECISION | DEFAULT 0 | 时长（秒） |
| created_at | TIMESTAMP | DEFAULT NOW, INDEX(DESC) | 上传时间 |

---

## 前端字段映射

| 前端接口字段 | 后端字段 | 转换方式 |
|-------------|----------|----------|
| post.slug | post.slug | 直接 |
| post.title | post.title | 直接 |
| post.description | post.description | 直接 |
| post.date | post.published_at | 格式化为 YYYY-MM-DD |
| post.category | Category.name | FK 查询取 name |
| post.tags | Tag.name[] | PostTag 中间表查询，组装为数组 |
| post.cover | post.cover | 直接 |
| chatter.slug | — | 说说用 id，不需 slug |
| chatter.title | chatter.content 前20字 | 截取 |
| chatter.description | chatter.content | 直接 |
| chatter.date | chatter.created_at | 格式化 |
| chatter.tags | — | 说说暂不支持标签 |
