# GrowthHub 架构决策

日期：2026-10-15。

- 单仓库、Spring Boot 模块化单体、React SPA，中文个人工作台。
- 已选版本：Java 21、Spring Boot 4.1.1、Maven 3.9.12；React 19、Vite 8，前端具体补丁以 package-lock.json 为准。
- 官方当前稳定 Spring Boot 支持 Java 21；已验证 Maven Central 存在对应版本。采用 MVC，不引入 WebFlux、Agent 框架或额外 Python 业务服务。
- Ubuntu 26.04 官方源提供 PostgreSQL 18.6，因此将原计划 PostgreSQL 17 更新为 18。本地采用原生 PostgreSQL 服务，避免为第一轮开发增加 Docker Desktop 启动依赖；后续容器也使用相同主版本。infra/compose.yml 是可选替代，映射端口 5433，不能与本地服务共享数据目录。
- JPA 实体不直接返回浏览器；采用 DTO、显式事务、版本号冲突检查、Flyway 迁移。小型目标/日志模块的应用事务暂与 REST 层同文件，AI 与复杂复盘功能接入前进一步拆分应用服务。
- 日期 date、事件 timestamptz；接口默认上海业务日期，与服务器时区解耦。
- 无密码公开数据库、全局 CORS、浏览器 API 密钥配置均不采用。当前接口无登录，只允许 loopback，本版本不用于公开服务器。
- 当前已实现目标/任务/日志、AI 教练、周工作台、成果、资讯候选及认证部署模块。资讯范围按 2026-10-15 用户确认调整为 14 个国内内容平台；Feed 模块按平台选择直接 RSS、RSSHub 或 CSS 抓取适配器，并统一订阅和去重模型。用户主页必须匹配对应平台 URL 规则，网页不能提交任意抓取主机。用户可选配置来源 Cookie；只在配置 AES-GCM 密钥后加密存储。各平台可用性必须逐个实测，不能由适配器代码存在推断。

来源：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[React 版本](https://react.dev/versions)、[PostgreSQL 支持政策](https://www.postgresql.org/support/versioning/)。

资讯适配策略参考 [JuFlow 适配器实现](https://github.com/rockbenben/juflow/tree/main/backend/app/adapters)（查阅日期：2026-10-15）；HTML 解析使用 [jsoup](https://jsoup.org/download) 1.23.2。GrowthHub 按其接口/路由模式独立实现，没有复制其 Python 服务代码。

M2：OpenAI 使用严格 JSON Schema，DeepSeek 使用 JSON mode，服务端统一显式校验固定结构与业务约束；调度线程处理持久化分析，不引入额外服务。React 新功能集中在 coach.tsx，既有日志页面保存后自动定位日期并持久化筛选参数。

M4 切片一新增 weekly 业务包和 Flyway V3；周事实汇总按日期查询原有任务/日志，复盘冻结来源并人工确认。导出 v2 覆盖十张业务表；本轮不新增中间件或依赖，详见 docs/weekly-workspace.md。

完整初版新增 achievement/feed/work/security 包。V4 为成果、资讯、通用 AI 作业及任务周归属；V5 为记录类型和可保存时区；V6 为新 AI 作业版本元数据；V7 为多平台资讯订阅字段。业务访问仍为模块化单体、JPA/Flyway/PostgreSQL；Spring Security 增加会话/CSRF；Caddy 提供 TLS 与同源反代，详情见 deployment.md。资讯聚合参考 JuFlow 的适配器优先级（RSS → RSSHub → 页面抓取），不引入 JuFlow 的 Python/Celery/Redis 服务栈；页面抓取使用 jsoup。
