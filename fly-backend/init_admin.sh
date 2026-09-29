#!/bin/sh
# 首次初始化数据库后创建管理员账号（在 init.sql 之后执行，文件名 zz-* 保证顺序）。
# 密码取环境变量 ADMIN_INITIAL_PASSWORD：必须显式设置，脚本不设任何默认值，
# 避免默认凭证随仓库公开泄露。
set -eu

if [ -z "${ADMIN_INITIAL_PASSWORD:-}" ]; then
    echo "[init-admin] 错误：未设置 ADMIN_INITIAL_PASSWORD，拒绝创建管理员账号。" >&2
    echo "[init-admin] 请在 .env 中将其设置为强密码后重新初始化数据库。" >&2
    exit 1
fi
pw="$ADMIN_INITIAL_PASSWORD"

# 转义单引号，防止密码中的特殊字符破坏 SQL
pw_sql=$(printf '%s' "$pw" | sed "s/'/''/g")

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO "user" (username, hashed_password, nickname, is_admin)
VALUES ('feifei', crypt('$pw_sql', gen_salt('bf', 10)), '管理员', true)
ON CONFLICT (username) DO NOTHING;
SQL

echo "[init-admin] 管理员账号 feifei 初始化完成"
