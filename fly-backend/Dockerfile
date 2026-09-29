# syntax=docker/dockerfile:1

# ---- 阶段 1：构建管理后台（Vue 3 + Vite） ----
# 注意：工作目录不能命名为 build（会触发 vite 配置路径解析 bug）
FROM node:22-alpine AS admin-build
ARG NPM_REGISTRY=https://registry.npmmirror.com
WORKDIR /app
ENV HUSKY=0 \
    NODE_OPTIONS=--max-old-space-size=4096 \
    COREPACK_NPM_REGISTRY=${NPM_REGISTRY}
RUN corepack enable && pnpm config set registry ${NPM_REGISTRY}
# .npmrc 必须随依赖清单一起拷入（shamefully-hoist 依赖它，否则 tippy.js 等间接依赖无法解析）
COPY admin/package.json admin/pnpm-lock.yaml admin/.npmrc ./
# pnpm 12 忽略 .npmrc 中的旧式设置，需在 pnpm-workspace.yaml 中声明（键名为 camelCase）：
# shamefullyHoist：hoist 传递依赖（tippy.js 直接 import 需要）
# dangerouslyAllowAllBuilds：允许依赖的构建脚本，避免 ERR_PNPM_IGNORED_BUILDS
RUN printf 'shamefullyHoist: true\ndangerouslyAllowAllBuilds: true\n' > pnpm-workspace.yaml
RUN --mount=type=cache,id=pnpm-backend,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile
COPY admin/ ./
RUN pnpm build

# ---- 阶段 2：构建 Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
# 阿里云 Maven 镜像（国内加速）
RUN mkdir -p /root/.m2 && printf '%s\n' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>aliyun</id>' \
    '      <mirrorOf>central</mirrorOf>' \
    '      <url>https://maven.aliyun.com/repository/public</url>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' > /root/.m2/settings.xml
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository mvn -q -B -DskipTests package

# ---- 阶段 3：运行时 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV LANG=C.UTF-8
COPY --from=backend-build /workspace/target/fly-backend.jar app.jar
COPY --from=admin-build /app/dist ./admin/dist
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
