#!/usr/bin/env bash
# acme.sh 续签成功后的热加载钩子（acme.sh --install-cert 的 --reloadcmd 调用本脚本）
# 新证书已由 acme.sh 写入 deploy/certs/（fullchain.pem / privkey.pem），reload 让 nginx 立即生效
cd "$(dirname "$0")/.." || exit 1
exec docker compose exec -T nginx nginx -s reload
