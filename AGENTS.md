# GrowthHub 项目约定
- 始终使用中文与用户沟通。
- 唯一源码目录：/home/yi/projects/GrowthHub，项目名称 GrowthHub。
- 开始开发前，先阅读 docs/documentation-standards.md、docs/development-plan.md、docs/environment.md 和 docs/progress.md；依据任务继续阅读相关架构、接口、AI 或部署文档。
- 文档按 docs/documentation-standards.md 维护。涉及功能、API、数据/架构、配置/运维、AI 契约或验收状态的改动，必须在同一交付中更新对应文档；计划基线变更须用户确认。新增专题文档要补 README 或相关文档导航。
- 严格区分计划、实现和验证事实；不把建议写成已确认计划，不把实现存在写成已验证。文档应标明未验证项和证据范围。
- 不在仓库内放 JDK/Maven/PostgreSQL 数据文件；不提交 .env、密钥和备份。
- 业务日期默认 Asia/Shanghai；AI 建议必须人工确认，不能直接修改正式计划。
- 使用 Flyway 演进数据库；测试必须使用独立 growthhub_test 数据库。
- 用户已授权在 WSL 安装所需环境和实施本项目，无需重复询问常规安装或可逆开发操作。
