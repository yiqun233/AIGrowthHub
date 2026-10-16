# GrowthHub

个人 AI 成长工作台。已实现目标、任务、分类日志、每日 AI 建议、周计划与任务归属、AI 周复盘、成果管理、资讯订阅（支持粘贴链接自动识别平台和批量添加）、收藏与候选转任务，以及导出、备份恢复、单用户登录和 HTTPS 部署包。

## 本机开发启动

项目在 WSL Ubuntu 中运行，源码目录为 `/home/yi/projects/GrowthHub`。需要 Java 21、Node.js 24、npm 和 PostgreSQL 18；安装位置及当前环境记录见[环境说明](docs/environment.md)。

首次在新环境运行时，先准备 PostgreSQL 的 `growthhub` 开发库和 `growthhub_test` 测试库，再复制 `.env.example` 为项目根目录下的私有 `.env`，将 `DB_PASSWORD` 改为 `growthhub` 数据库账号的实际密码。`.env` 已存在时不要覆盖。具体环境准备见[环境说明](docs/environment.md)。AI 提供商密钥为可选项，不配置时仍可使用手工任务、日志和周工作台。

```bash
cd /home/yi/projects/GrowthHub

# 仅首次配置时执行；已有 .env 时跳过，避免覆盖本地配置
if [ ! -f .env ]; then cp .env.example .env && chmod 600 .env; fi
# 首次配置后，用编辑器填写 .env 中正确的 DB_PASSWORD

cd backend && ./mvnw package && cd ..
cd frontend && npm ci && cd ..
python3 scripts/dev.py start
```

启动脚本会在后台启动后端和 Vite 开发服务器，并等待健康检查通过。打开 <http://127.0.0.1:5173> 使用；后端健康检查地址为 <http://127.0.0.1:8080/actuator/health>。默认免登录，仅监听本机回环地址。启动状态、停止和日志位置：

```bash
cd /home/yi/projects/GrowthHub
python3 scripts/dev.py status
python3 scripts/dev.py stop
# 日志位于 .runtime/backend.log 和 .runtime/frontend.log
```

修改后端代码或依赖后重新打包，再停止并启动服务；前端开发服务器会自动热更新：

```bash
cd /home/yi/projects/GrowthHub/backend && ./mvnw package
cd /home/yi/projects/GrowthHub && python3 scripts/dev.py stop && python3 scripts/dev.py start
```

认证、容器部署、HTTPS、备份恢复和生产运行方式见[运行与部署手册](docs/deployment.md)。

## 验证

```bash
python3 scripts/test.py
python3 scripts/security_test.py
RUN_HTTPS_TEST=true python3 scripts/security_test.py
python3 scripts/backup_policy_test.py
```

测试只使用 growthhub_test，完整回归会清空该库。浏览器验收使用生产构建。认证与普通回归顺序执行，避免同一测试库运行多个恢复扫描。真实提供商密钥仅保存在服务端，不写入浏览器、数据库或 Git。

## 文档

- [文档编写与维护规范](docs/documentation-standards.md)：文档职责、更新时机、事实状态、证据和安全要求。
- [逐项计划核对](docs/plan-audit.md)：已实现范围与外部待验收项。
- [运行与部署](docs/deployment.md)：本机、登录、容器、HTTPS、自动备份。
- [完整功能接口](docs/remaining-features.md)：成果、周归属、资讯、周 AI、认证、导出 v3。
- [模型提供商](docs/model-providers.md)：十类提供商，模型 ID 自由配置。
- [环境与安装位置](docs/environment.md)、[当前进度](docs/progress.md)。
- [原开发计划](docs/development-plan.md)、[AI 每日契约](docs/ai-contract.md)、[七天试用](docs/m3-trial.md)。

初版业务代码不等于全部外部验收完成：真实模型、七天观察和公网/独立存储仍需实际条件。用户已确认暂时没有服务器，本轮交付本机与部署包，不进行公网发布。
