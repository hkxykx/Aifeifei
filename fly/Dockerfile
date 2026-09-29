# syntax=docker/dockerfile:1

# ---- 阶段 1：构建 Next.js 前端 ----
FROM node:22-alpine AS build
ARG NPM_REGISTRY=https://registry.npmmirror.com
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1 \
    NODE_OPTIONS=--max-old-space-size=4096 \
    COREPACK_NPM_REGISTRY=${NPM_REGISTRY}
RUN corepack enable && pnpm config set registry ${NPM_REGISTRY}
COPY package.json pnpm-lock.yaml ./
# pnpm 12 忽略 .npmrc 中的旧式设置；允许依赖构建脚本避免 ERR_PNPM_IGNORED_BUILDS
RUN printf 'dangerouslyAllowAllBuilds: true\n' > pnpm-workspace.yaml
RUN --mount=type=cache,id=pnpm-frontend,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile
COPY . .

# SSR 首页数据抓取用的后端地址（容器网络内为 http://backend:8000）
ARG NEXT_PUBLIC_API_URL=http://backend:8000
# 阅读服务地址（可选，未部署时留空）
ARG NOVEL_API_URL=
# rewrite 与 next/image 优化器的后端地址：rewrites() 在构建时求值，必须在 build 前设为 ENV
ENV API_INTERNAL_URL=http://backend:8000 \
    NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL} \
    NOVEL_API_URL=${NOVEL_API_URL} \
    NEXT_OUTPUT=standalone

RUN pnpm build

# ---- 阶段 2：运行时（仅 standalone 产物，不含源码与全量 node_modules）----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    NEXT_PUBLIC_API_URL=http://backend:8000 \
    HOSTNAME=0.0.0.0 \
    PORT=3000

# standalone = server.js + 按需裁剪的 node_modules（pnpm 全量依赖留在 build 层）
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/public ./public
COPY --from=build /app/.next/static ./.next/static
# 关于页在请求时读取 Markdown，standalone 追踪不到该相对路径，需显式带入
COPY --from=build /app/app/about/about.md ./app/about/about.md

EXPOSE 3000
CMD ["node", "server.js"]
