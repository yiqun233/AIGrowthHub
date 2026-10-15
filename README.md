# GrowthHub

个人 AI 技术成长工作台。已实现目标、任务、分类日志、每日 AI 建议、周计划与任务归属、AI 周复盘、成果管理、两源资讯、收藏与候选转任务，以及导出、备份恢复、单用户登录和 HTTPS 部署包。

## 启动

唯一源码目录：/home/yi/projects/GrowthHub。当前机器已有私有 .env；新环境从 .env.example 建立配置。

```bash
cd /home/yi/projects/GrowthHub
cd backend && ./mvnw verify && cd ..
cd frontend && npm ci && npm run build && cd ..
python3 scripts/dev.py start
```

本机入口：http://127.0.0.1:5173。状态/停止：python3 scripts/dev.py status / stop。默认免登录只允许回环监听；启用账号或容器部署详见运行手册。

## 验证

```bash
python3 scripts/test.py
python3 scripts/security_test.py
RUN_HTTPS_TEST=true python3 scripts/security_test.py
python3 scripts/backup_policy_test.py
```

测试只使用 growthhub_test，完整回归会清空该库。浏览器验收使用生产构建。认证与普通回归顺序执行，避免同一测试库运行多个恢复扫描。真实提供商密钥仅保存在服务端，不写入浏览器、数据库或 Git。

## 文档

- [逐项计划核对](docs/plan-audit.md)：已实现范围与外部待验收项。
- [运行与部署](docs/deployment.md)：本机、登录、容器、HTTPS、自动备份。
- [完整功能接口](docs/remaining-features.md)：成果、周归属、资讯、周 AI、认证、导出 v3。
- [模型提供商](docs/model-providers.md)：十类提供商，模型 ID 自由配置。
- [环境与安装位置](docs/environment.md)、[当前进度](docs/progress.md)。
- [原开发计划](docs/development-plan.md)、[AI 每日契约](docs/ai-contract.md)、[七天试用](docs/m3-trial.md)。

初版业务代码不等于全部外部验收完成：真实模型、七天观察和公网/独立存储仍需实际条件。用户已确认暂时没有服务器，本轮交付本机与部署包，不进行公网发布。
