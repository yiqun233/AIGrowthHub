# GrowthHub 项目约定
- 始终使用中文与用户沟通。
- 唯一源码目录：/home/yi/projects/GrowthHub，项目名称 GrowthHub。
- 查看 docs/development-plan.md、docs/environment.md 和 docs/progress.md 后再推进开发。
- 不在仓库内放 JDK/Maven/PostgreSQL 数据文件；不提交 .env、密钥和备份。
- 业务日期默认 Asia/Shanghai；AI 建议必须人工确认，不能直接修改正式计划。
- 使用 Flyway 演进数据库；测试必须使用独立 growthhub_test 数据库。
- 用户已授权在 WSL 安装所需环境和实施本项目，无需重复询问常规安装或可逆开发操作。
