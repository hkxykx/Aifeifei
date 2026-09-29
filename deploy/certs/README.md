# HTTPS 证书目录

把证书文件放到本目录（nginx 容器只读挂载到 `/etc/nginx/certs`）：

- `fullchain.pem` — 证书链（腾讯云 Nginx 格式下载包里的 `域名_bundle.crt`，改名即可）
- `privkey.pem`   — 私钥（包里的 `域名.key`，改名即可）

`deploy/nginx.conf` 的 443 server 段引用这两个文件名。

配置 acme.sh 自动续签后（见 `docs/运维手册.md` 6.5），本目录的证书由 acme.sh 在
每 60 天续签时自动覆盖更新并热加载 nginx，无需手动维护。

注意：
- 放好证书前不要 `docker compose up -d nginx` 应用新配置，否则 nginx 因读不到证书
  启动失败，80 端口会一起挂掉。
- 更换证书（续期）后执行 `docker compose exec nginx nginx -s reload` 热加载即可。
- 私钥文件不要提交到任何仓库，服务器上建议 `chmod 600 privkey.pem`。
