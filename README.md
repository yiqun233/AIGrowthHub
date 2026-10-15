# GrowthHub

个人 AI 技术成长助手。当前已开始实施 M0/M1：先管理长期目标、学习任务和原始学习记录，再接入 AI 建议与复盘。

## 在 WSL 中启动

```bash
cd /home/yi/projects/GrowthHub
# 当前机器的 .env 已生成；新环境请从 .env.example 建立独立配置。
cd backend && ./mvnw -B -ntp package && cd ..
cd frontend && npm ci && npm run build && cd ..
python3 scripts/dev.py start
```

浏览器访问 http://127.0.0.1:5173。检查和停止：python3 scripts/dev.py status / stop。前台开发可分别运行 bash scripts/backend.sh 和 bash scripts/frontend.sh。

先创建长期目标，再建任务；完成后写日志并关联任务。延期通过任务编辑修改日期；已完成任务可以重开；日志保留原文，修改时检查版本避免覆盖新记录。当前不包含示例假数据。

## 验证

```bash
cd backend && ./mvnw -B -ntp verify && cd ..
cd frontend && npm run build && npx playwright install chromium && cd ..
python3 scripts/test.py
# 仅真实接口回归
python3 scripts/test.py --api-only
```

测试启动独立后端并只连接 growthhub_test。浏览器测试通过专用 Vite 端口代理到测试后端；不会把测试学习记录混进日常使用数据库。

## 文档

- docs/development-plan.md：开发实施计划与版本范围。
- docs/environment.md：安装版本、路径、数据、日志、凭据与运维方式。
- docs/architecture.md：技术决策与计划调整。
- docs/api.md：已实现接口与限制。
- docs/progress.md：里程碑进度与验证结果。

当前限本机使用，尚未启用登录。AI 提供商、建议确认、周复盘、资讯、公开服务器部署将按计划继续，不会把未完成能力展示为可用功能。
