> 这是 M0/M1 完成时的历史快照；当前 M2 状态和配置请查看 docs/progress.md 与 docs/ai-contract.md。

# GrowthHub 实施进度

更新日期：2026-10-15。

## 已完成 M0：环境和骨架

- 确认唯一源码目录 /home/yi/projects/GrowthHub；项目名 GrowthHub。
- 安装 Ubuntu 原生 Java 21、Maven 3.9.12、PostgreSQL 18.6；复用已有 Volta Node 24.18.1。
- 建立 Spring Boot 4.1.1、React SPA、Flyway、Maven Wrapper、可选数据库 Compose 和 CI 配置。
- 本地 Git 已初始化到 main，未提交、未设置远端、未推送；.env、缓存、构建产物和备份均被忽略。

## 已完成 M1：手工学习闭环

- 中文今日页、任务列表与筛选、长期目标管理、学习日志新增/编辑和关联任务。
- 任务创建、修改、延期、开始、完成、取消和重开；目标归档保留历史。
- 日志保留原文、实际时长、成果和阻塞；允许同日多条与休息记录。
- 服务端输入校验、DTO、乐观版本冲突、事务关联校验、日期边界处理。
- 加载/空数据/错误/重试状态；桌面和手机布局。
- 本机启动/停止/状态脚本、受限账号、私有 .env、备份/恢复脚本与说明。

## 验证结果

- Maven Wrapper verify：通过（当前 Maven 生命周期验证编译与打包；真实业务回归在下述 API 测试执行，不宣称有尚未编写的 Java 单元测试）。
- TypeScript + Vite 生产构建：通过。业务代码与 React/UI 依赖已分包；UI 依赖包仍有体积提示，后续按需懒加载优化，不影响当前本机使用。
- 10 项真实 PostgreSQL/API 回归：全部通过，覆盖原文保存、完成/重开、延期、版本冲突、输入错误、无效关联事务、归档和查询。
- 2 项 Chromium 浏览器验收：全部通过，覆盖创建目标→创建/完成任务→保存/编辑日志→刷新持久化，以及手机宽度/错误重试。
- 备份恢复：将独立测试库恢复到 growthhub_restore_test，逐行比较目标 5 条、任务 17 条、日志 13 条、关联 6 条，内容一致。
- 开发库仍为空，测试记录没有写入日常使用库。
- 已生成开发库首份备份。现有恢复演练库已含测试数据，再次演练请另建空 growthhub_restore_* 库，不覆盖已有库。
- CI 文件已生成并对应本地验证命令；尚未连接 GitHub，因此没有远端 CI 执行结果。

## 当前运行和范围

前端 http://127.0.0.1:5173，后端 127.0.0.1:8080，数据库 localhost:5432。当前限本机，无登录。运行状态以 python3 scripts/dev.py status 为准。

下一里程碑 M2：提供商适配、分析状态与快照、结构化校验、建议编辑/拒绝/幂等确认。真实联调前再配置 API 密钥，无需把密钥发到聊天中。

尚未交付：AI 学习建议、周计划/复盘、资讯、数据导出接口、公开服务器部署与认证。当前设置页只显示实际状态，不提供虚假的可用按钮。

## 前端锁定版本

- @ant-design/icons：6.3.4
- @tanstack/react-query：5.104.2
- antd：6.6.6
- dayjs：1.11.23
- react：19.3.1
- react-dom：19.3.1
- react-router-dom：7.18.4

Windows 原生 HTTP 校验：明确 IPv4 地址 http://127.0.0.1:5173 返回 200。完整验收已通过，无需再调整全局网络设置。


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
