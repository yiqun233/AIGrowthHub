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
