# 部署验收记录

日期：2026-10-15。隔离项目 growthhub-release-check，数据与原生开发库分离。

## 已实际执行

- 后端、Web、数据库初始化/备份四个镜像构建成功。
- docker compose up --build --wait 启动成功；数据库与后端健康检查通过。
- TLS 证书链与主机名、Secure/HttpOnly 会话、CSRF、SPA 路由和 CSP 验证通过。
- 真实 Chromium 登录、保存日志、退出与未登录禁止导出通过。
- 应用账号非 superuser/createdb/createrole；后端运行身份 10001:10001，根文件系统只读。
- 仅 Web 在回环绑定 18085/18443；数据库/后端没有宿主发布端口。
- 自动生成并验证每日/每周备份；恢复到新空库，15 张业务表和 Flyway 历史逐行一致。
- 可重复 deployment_check.py 完成构建/启动/验收/停止；没有删除持久卷。

## 本次验收镜像标识

| 服务 | 实际镜像 ID |
| --- | --- |
| backend | sha256:cd62fe201814ae016dfe1d042526eae2e3372196ca311669c2c66aada00ae9ff |
| web | sha256:d18201323f02abf9e4eb55f2d41cc133938d1e7ad339238e862d4a8a748af707 |
| db | sha256:8579c9bb6057d27484348e13dbaea5234474c85f42c0f10e186ab99b0a50dbe8 |
| backup | sha256:200f8aac7a465ed75bbe120f17b17ab05349092c5510fd80398b334d733a1aa0 |

## 容器恢复记录

独立恢复库：growthhub_restore_check_1792056123。

| 表 | 行数 |
| --- | --- |
| achievement | 0 |
| ai_analysis | 0 |
| ai_work | 0 |
| app_setting | 1 |
| feed_candidate | 0 |
| feed_item | 20 |
| feed_source | 2 |
| flyway_schema_history | 6 |
| learning_goal | 0 |
| learning_task | 0 |
| plan_suggestion | 0 |
| study_log | 3 |
| study_log_task | 0 |
| suggestion_task | 0 |
| weekly_plan | 0 |
| weekly_review | 0 |

Docker Desktop 29.6.2 / Compose 5.3.1。初次 Docker Hub 匿名 token 请求 EOF 后，使用 public.ecr.aws/docker/library 的 Docker 官方镜像集合完成验收，未修改代理。首次 WSL 宿主脚本挂载失败后，改为镜像内打包初始化/备份/本机 Caddy 配置，不修改全局 WSL 集成。

验收后停止专用容器并释放端口，保留 images 与命名卷以便复查。原生服务仍为 http://127.0.0.1:5173。临时测试凭据仅在 .runtime/container-test.env（0600），不进入源码包。

公网域名、真实公开证书、独立远端存储与远端 CI 执行结果未以本机验收代替。
