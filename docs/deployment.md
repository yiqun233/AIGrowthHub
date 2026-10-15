# GrowthHub 本机与 Linux 部署运行手册

本机业务默认 http://127.0.0.1:5173；唯一源码 /home/yi/projects/GrowthHub。用户确认暂时没有服务器，本轮交付部署包，不进行公网发布。

## 本机

```bash
cd /home/yi/projects/GrowthHub
cd backend && ./mvnw verify && cd ..
cd frontend && npm ci && npm run build && cd ..
python3 scripts/dev.py start
python3 scripts/dev.py status
```

私有 .env 中的 AUTH_ENABLED=false 只允许监听回环地址。需要本机登录时设置 AUTH_ENABLED=true、AUTH_USERNAME 和至少 12 字符的 AUTH_PASSWORD 后重启；本机 HTTP 使用 SESSION_COOKIE_SECURE=false。密码只写私有配置，不提交、不发送到聊天。

非回环后端监听必须开启认证，否则启动失败。身份认证使用 Spring Security Session、BCrypt、会话轮换和 CSRF，不使用浏览器持久化 JWT。会话默认 30 分钟；嵌入式 Servlet 容器超时粒度按分钟测试。生产 Cookie 使用 HttpOnly、Secure、SameSite=Strict。登录尝试限每分钟 15 次（单用户全局限制）。重启会使内存会话失效，重新登录即可。

设置页可保存 IANA 业务时区，如 Asia/Shanghai、UTC；未保存时沿用 APP_TIMEZONE。任务/学习日期保持原 date，不迁移历史日期；时间边界与“今天”使用业务时区。日志分学习、开发实践、休息，休息必须 0 分钟，两类实际时长分别统计后只加总一次。

## 容器部署包

- infra/backend.Dockerfile：Maven/Java 21 多阶段构建，运行账号 10001，健康检查与只读文件系统。
- infra/frontend.Dockerfile：Node 24.18.1 构建静态站点，Caddy 2.11.7 提供 HTTPS 和同源 /api 反代。
- infra/compose.prod.yml：PostgreSQL 18.6、后端、入口和备份服务；数据库与后端不映射宿主端口。数据库管理员与受限应用账号分离。
- infra/postgres.Dockerfile：将受控初始化/备份脚本打包到 PostgreSQL 镜像，避免宿主路径依赖。
- infra/compose.local.yml：使用镜像内本机 TLS 配置，不向系统自动安装 CA。
- infra/backup-loop.sh：每日备份、每周首份副本，保留最近约 7 天/4 周；备份有效后才清理旧文件，失败保留原副本；损坏的当日文件会重建。

在 Linux 主机安装可用 Docker Engine / Compose 后，在项目根目录执行：

```bash
cp infra/.env.production.example infra/.env.production
chmod 600 infra/.env.production
# 在编辑器填写独立 PG_ADMIN_PASSWORD、DB_PASSWORD、AUTH_PASSWORD（至少 12 字符）
# 本机演练保留 BIND_ADDRESS=127.0.0.1、HTTP_PORT=18085、HTTPS_PORT=18443
docker compose --env-file infra/.env.production -f infra/compose.prod.yml -f infra/compose.local.yml config --quiet
docker compose --env-file infra/.env.production -f infra/compose.prod.yml -f infra/compose.local.yml up -d --build
```

本机演练入口 https://localhost:18443。测试证书是私有 CA，浏览器默认不信任；仅在明确需要时导出该测试 CA，测试程序使用独立信任上下文，不修改系统信任。实际公网使用自己控制的域名，不加载 compose.local.yml。

公网发布前填写 APP_DOMAIN、ACME_EMAIL；将 DNS 指向实际主机，只开放入口 80/443，并明确设置 BIND_ADDRESS=0.0.0.0、HTTP_PORT=80、HTTPS_PORT=443。使用生产 Caddyfile 获取真实域名证书。该步骤尚未执行，因为没有服务器或域名。

```bash
docker compose --env-file infra/.env.production -f infra/compose.prod.yml config --quiet
docker compose --env-file infra/.env.production -f infra/compose.prod.yml up -d --build
docker compose --env-file infra/.env.production -f infra/compose.prod.yml ps
docker compose --env-file infra/.env.production -f infra/compose.prod.yml logs --tail=100 backend web backup
```

API 密钥、模型、基地址、输出模式和 token 参数通过生产私有配置注入，十类提供商均透传，见 model-providers.md。自动资讯刷新每 15 分钟运行，失败只更新来源状态。无需 Redis、消息队列或向量库。

示例镜像标签是已写定版本系列；Java/Maven 的基础镜像仍为维护中的系列标签，正式上线应将已构建验证的镜像固定到 digest，并记录解析值。本轮已复用现有 Docker Desktop 完成四镜像构建、隔离容器启动、HTTPS、登录和全表恢复验收，实际标识见 deployment-verification.md。两套 Compose 配置与可重复部署脚本已加入 CI；远端执行结果须实际查看。

## 备份、迁移与恢复

本机：bash scripts/backup.sh；恢复仅允许独立、空的 growthhub_restore_* 数据库。已有库不会被脚本覆盖。

容器备份由 backup_data 卷保存。磁盘卷不等于独立备份，需要另外复制到自己的离线设备或受限远端存储；本轮没有目标存储账号，不配置虚假异地备份。

```bash
mkdir -p backups/container-copy
cid=$(docker compose --env-file infra/.env.production -f infra/compose.prod.yml ps -q backup)
docker cp "$cid:/backups/." backups/container-copy/
```

数据库恢复必须先建新空库，使用同版本 pg_restore --exit-on-error --no-owner --no-privileges 恢复，再逐表核对记录和关系。容器数据库的应用账号没有建库权限，应由数据库管理员创建恢复库并将 owner 设为 growthhub。不要在失败升级后直接删除数据卷；先保留备份，必要时恢复到新卷/空库后切换。新迁移通过 Flyway 增量演进，不修改已应用迁移。

## 验证命令

```bash
python3 scripts/test.py                # 清空并仅使用 growthhub_test
python3 scripts/security_test.py       # 独立 18081；真实登录/CSRF/会话过期和浏览器
RUN_HTTPS_TEST=true python3 scripts/security_test.py
python3 scripts/backup_policy_test.py
```

HTTPS 验收使用 CADDY_BIN，默认 /home/yi/.local/share/growthhub/tools/caddy/2.11.7/caddy，已按官方 SHA-512 校验。测试监听仅回环 18443，证书/配置在 .runtime/caddy-smoke；不修改全局代理或证书。上述数据库测试、认证测试应顺序执行，不对同一测试库并发启动多个应用，以免恢复扫描互相影响。

仍需外部验收：真实模型权限/网络/质量；七个自然日实际使用；真实域名证书、公网和独立存储恢复。没有这些证据不能声称已公网部署或全部验收完成。


## 可重复的隔离部署验收

```bash
python3 scripts/deployment_check.py
# Docker Hub 网络受限时，可选择 AWS ECR 的 Docker 官方镜像集合：
BASE_REGISTRY=public.ecr.aws/docker/library python3 scripts/deployment_check.py
```

脚本仅使用 growthhub-release-check 项目与回环 18085/18443，自动生成私有测试凭据，构建、启动、运行容器测试后停止容器，保留数据卷。本机 WSL 未开启 Docker 集成时，可通过 DOCKER_BIN 指定现有 Windows docker.exe 的 /mnt/c/... 路径；不需要复制源码或修改全局代理。不要同时运行本机原生 TLS 测试，因为它也使用 18443。

Docker 官方镜像在 AWS ECR 的发布依据：[AWS 官方说明](https://aws.amazon.com/blogs/containers/docker-official-images-now-available-on-amazon-elastic-container-registry-public/)。这是可选镜像注册表，不是第三方重打包镜像。

本机 .env 支持单行引号值（例如包含空格、#、$ 的密码）与 export 前缀；值按字面读取，不进行 shell 命令或变量替换。生产 Compose 使用自己的 dotenv 规则，含 $ 的字面量建议使用单引号。启用认证会拒绝未替换的 replace-with- 示例密码。
