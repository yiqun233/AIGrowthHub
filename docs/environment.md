# WSL 开发环境与安装记录

安装日期：2026-10-15。发行版 Ubuntu，Ubuntu 26.04 LTS，WSL2。用户 yi (UID 1000)。

## 安装与位置

| 项目 | 版本/来源 | 安装或数据位置 |
| --- | --- | --- |
| 源码 | GrowthHub | /home/yi/projects/GrowthHub |
| Java | OpenJDK 21.0.12.1，Ubuntu 官方 apt | /usr/lib/jvm/java-21-openjdk-amd64，命令 /usr/bin/java、/usr/bin/javac |
| Maven | 3.9.12，Ubuntu 官方 apt | /usr/share/maven，命令 /usr/bin/mvn；缓存 ~/.m2/repository |
| Maven Wrapper | 项目生成，Maven 3.9.12 | backend/mvnw 与 backend/.mvn；下载缓存 ~/.m2/wrapper |
| Node | 24.18.1，已有 Volta 环境，未重装 | /home/yi/.volta/bin/node，实际映像 ~/.volta/tools/image/node/24.18.1 |
| npm | 11.16.0，已有环境 | /home/yi/.volta/bin/npm；缓存 ~/.npm |
| PostgreSQL | 18.6，Ubuntu 官方 apt | /usr/lib/postgresql/18/bin；工具 /usr/bin/psql、pg_dump、pg_restore |
| 数据库数据 | cluster 18/main | /var/lib/postgresql/18/main，归属 postgres |
| 数据库配置 | cluster 18/main | /etc/postgresql/18/main/postgresql.conf、pg_hba.conf |
| 数据库日志 | 系统服务日志 | /var/log/postgresql/postgresql-18-main.log |
| 配置与密钥 | 随机本地数据库密码 | 项目 .env，yi 所有，权限 0600，不提交 |
| 应用日志/PID | 开发期 | 项目 .runtime/，不提交 |
| 数据备份 | pg_dump 自定义格式 | 项目 backups/，权限受限，不提交 |

执行的安装命令：apt-get update；apt-get install openjdk-21-jdk-headless maven postgresql-18 postgresql-client-18。仅新增所需软件包，未执行全系统升级或 autoremove。

已有 Windows Maven 和 Docker 路径仍保留，没有修改用户全局 PATH 或其他项目配置。GrowthHub 脚本明确使用 Linux Java/Maven，前端复用 Volta。使用脚本运行时不需要手工修改系统环境变量。

数据库服务：systemctl enable --now postgresql；查看 pg_lsclusters 或 systemctl status postgresql@18-main。监听 localhost:5432，未改为 0.0.0.0，未开启远程数据库访问。WSL 停止时应用随之停止，PostgreSQL 随 WSL systemd 启动；前后端需手动启动。

账号 growthhub：LOGIN、无 superuser/createdb/createrole 权限。数据库 growthhub（开发）、growthhub_test（自动测试）、growthhub_restore_test（恢复演练）均由该账号持有。用户名并非密码，密码只在 .env 中。

## 运维命令

```bash
cd /home/yi/projects/GrowthHub
java -version
/usr/bin/mvn -version
node --version
pg_lsclusters
python3 scripts/dev.py start
python3 scripts/dev.py status
python3 scripts/dev.py stop
bash scripts/backup.sh
```

开发端口：前端 5173、后端 8080；隔离测试：前端 15173、后端 18080，测试数据不会写入开发数据库。可选容器数据库使用 5433，需要手工将 DB_URL 改为对应端口，不要把两份数据混用。

Playwright Chromium 仅用于浏览器验证，安装到 ~/.cache/ms-playwright；Node 模块在 frontend/node_modules，Maven 构建产物在 backend/target，两者不提交。

完整恢复前先新建空的 growthhub_restore_* 数据库，再执行 bash scripts/restore.sh <备份文件> <目标库>；脚本拒绝覆盖开发库，也拒绝向非空库恢复。生产自动备份、独立存储及发布认证属于后续里程碑。

Maven Wrapper 生成时沿用了用户已有的阿里云 Maven 镜像（仅下载源，不修改全局 settings.xml）；后续依赖下载问题先查该镜像。浏览器验证已安装 Chromium 1248 和 FFmpeg 1013 到 ~/.cache/ms-playwright，未安装系统 GUI 浏览器。

本机 HTTP 验证使用不经过代理的 Python opener，避免继承代理导致 loopback 请求卡住；没有更改用户全局代理配置。

恢复演练需要空库，例如 `sudo -u postgres createdb -O growthhub growthhub_restore_20261016`，再执行恢复脚本；此命令需数据库管理员权限。

Windows 访问核验：`http://127.0.0.1:5173` 返回 HTTP 200，页面包含 GrowthHub。当前 mirrored + autoProxy 环境下 localhost 请求曾超时，建议使用明确 IPv4 地址；未更改 .wslconfig、代理、防火墙或端口转发。

## M7 学习主题阶段增量（2026-10-16）

未安装新环境组件。Flyway V8 已于 2026-10-16 17:18（Asia/Shanghai）成功应用到本机开发库 `growthhub`；启动日志确认 schema 到 v8，后端于 17:18 启动成功。V8 新增阶段/阶段复盘表，并为任务、日志、周计划和成果添加可空阶段外键，不回填既有记录。

迁移后开发库备份：`backups/growthhub-20261016T091936Z-1428978.dump`。新数据导出 schema 为 `growthhub-export-v4`，覆盖 17 张业务表。Maven/前端构建证据及未运行测试范围见 `docs/progress.md`。


## M2 增量记录

未新增系统软件或 npm/Maven 业务依赖；沿用现有 Java/Node/PostgreSQL/Playwright。Flyway 新增 V2__ai_coach.sql，增加设置、分析、建议和确认映射表。模型密钥、基地址、默认模型、次数和超时配置见 .env.example 与 docs/ai-contract.md。这些配置仅在重启后端时重新读取。

本次新增隔离提供商模拟端口 18889，仅在 scripts/test.py 中运行，凭据和模型都是测试占位值；正式启动脚本不启用模拟服务。完整测试会清空 growthhub_test 的旧业务数据，不能作为长期学习数据存放位置。

升级前备份：backups/growthhub-20261015T071556Z-69033.dump。升级前后逐表比对确认原有数据未改变，数据库服务与数据目录均未迁移。


2026-10-15 主流模型扩展：未新增系统软件或数据库中间件。提供商密钥、模型和可选基地址仍放唯一源码目录的私有 .env；配置前缀见 docs/model-providers.md。原有 Java/Maven/Node/PostgreSQL 路径不变。开发库本轮备份：backups/growthhub-20261015T074641Z-81647.dump；本机服务已重启加载 10 类提供商与 JSON 导出。


## M4 周工作台切片一

未安装新环境组件。Flyway V3__weekly_workspace.sql 增加 weekly_plan、weekly_review；数据库仍在原 PostgreSQL 18/main 数据目录。

开发库升级前备份：backups/growthhub-20261015T080457Z-89144.dump。升级前后原八张业务表的逐行哈希一致；开发库新增两表为空，不含自动化测试记录。

独立恢复演练库：growthhub_restore_m4_20261015_080351（通过 WSL 管理员身份，以 postgres 为 growthhub 新建；应用账号权限没有扩大）。使用 backups/growthhub-test-m4-20261015_080423.dump 恢复当时的隔离测试库快照，十张业务表与 Flyway 历史逐行一致，包括周计划 3 条、周复盘 5 条。再次向非空库恢复被脚本阻止。比较报告保存在 .runtime/m4-restore-report.json，不提交。此库保留为演练结果，不用于开发或日常学习。


## 完整初版增量（2026-10-15）

- Spring Security 跟随 Boot BOM 增加会话/CSRF 依赖，未引入 Redis/MQ/向量库。
- Flyway V4/V5/V6：成果、资讯/候选、AI 作业、周归属触发器、日志类型、可保存时区、AI 提示词/结构版本。
- Flyway V7：资讯来源适配类型、订阅主页、启用状态与创建时间。
- 2026-10-15 本机启动日志确认 Flyway V7 已成功应用到开发库 `growthhub`；本次未在 `growthhub_test` 上运行测试。
- 资讯订阅使用 `RSSHUB_URL` 指向管理员选择的 RSSHub 实例，默认 `https://rsshub.app`；对应平台来源从该服务读取 Atom/RSS。集思录、淘股吧、同花顺、韭圈儿、有知有行通过允许列表中的平台主页抓取；CSDN 和 V2EX 使用直接 RSS。自建 RSSHub 可用 `http://rsshub:1200` 等容器内地址，须在相同容器网络下配置。
- 仅当需要登录态抓取时，私有 `.env` 可配置 `FEED_COOKIE_ENCRYPTION_KEY`，值为 `openssl rand -base64 32` 生成的密钥；绝不提交该值。更换密钥后，已保存的订阅 Cookie 将无法解密，需要在资讯页重新保存。Cookie 通过页面单独写入 AES-GCM 加密表，不进入数据导出。
- 本机已在私有 `.env` 生成随机 Cookie 加密密钥并保持权限 0600；未录入任何平台 Cookie。
- 新安装仅为本机 TLS 验收工具 Caddy 2.11.7，位置 /home/yi/.local/share/growthhub/tools/caddy/2.11.7/caddy；来源 GitHub caddyserver/caddy 官方 release，以官方 SHA-512 校验：a7a433a1b133efc3c8d10eb0b99d52a24b5ef5c322dc77f5282182b1c0402139ab83f3a99f0c52409df77d20123fb0b523edad8a66d8f5e49136197bf61ef0e7。未加入全局 PATH，未注册服务。
- TLS 测试数据/证书在项目 .runtime/caddy-smoke，权限受限，不提交。使用独立 SSL 上下文信任测试 CA，未安装系统根证书；未更改代理或防火墙。
- 认证测试后端 18081、生产静态预览 15174、本机 TLS 18443；进程测试结束后停止。
- 开发库升级前备份 backups/growthhub-20261015T090203Z-115397.dump；迁移前十表原有列投影逐行哈希一致（新增列不误判为旧值改变）。
- 完整恢复库 growthhub_restore_release_20261015；备份 backups/growthhub-test-release-20261015_090301.dump。15 张业务表与 Flyway 历史逐行一致，包括成果 1、资讯 2、候选 1、AI 作业 5、周计划 6、复盘 9；非空库恢复保护通过。报告 .runtime/release-restore-report.json。
- 复用已有 Windows Docker Desktop 29.6.2，未安装第二套 Docker Engine。镜像、缓存及卷由现有 Docker Desktop Linux Engine 管理，与源码/原生 PostgreSQL 分离。测试项目固定 growthhub-release-check，凭据仅在 .runtime/container-test.env（0600）。
- Docker Hub 取匿名 token 出现 EOF；本轮使用 BASE_REGISTRY=public.ecr.aws/docker/library，对应 AWS ECR 的 Docker 官方镜像集合，不修改系统代理。后续运行结果和镜像标识见 deployment-verification.md。

完整部署最终状态：Docker Engine 已启动并完成四镜像/独立容器验收，可通过 deployment_check.py 重复。初始化、备份和本机 Caddy 配置已打包进镜像，无需 WSL 宿主脚本挂载。验收后停止专用容器和网络，保留 growthhub-release-check 命名卷及构建缓存。最终镜像 ID、恢复库与表计数见 deployment-verification.md。
