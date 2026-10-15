# GrowthHub 架构决策

日期：2026-10-15。

- 单仓库、Spring Boot 模块化单体、React SPA，中文个人工作台。
- 已选版本：Java 21、Spring Boot 4.1.1、Maven 3.9.12；React 19、Vite 8，前端具体补丁以 package-lock.json 为准。
- 官方当前稳定 Spring Boot 支持 Java 21；已验证 Maven Central 存在对应版本。采用 MVC，不引入 WebFlux、Agent 框架或额外 Python 业务服务。
- Ubuntu 26.04 官方源提供 PostgreSQL 18.6，因此将原计划 PostgreSQL 17 更新为 18。本地采用原生 PostgreSQL 服务，避免为第一轮开发增加 Docker Desktop 启动依赖；后续容器也使用相同主版本。infra/compose.yml 是可选替代，映射端口 5433，不能与本地服务共享数据目录。
- JPA 实体不直接返回浏览器；采用 DTO、显式事务、版本号冲突检查、Flyway 迁移。小型目标/日志模块的应用事务暂与 REST 层同文件，AI 与复杂复盘功能接入前进一步拆分应用服务。
- 日期 date、事件 timestamptz；接口默认上海业务日期，与服务器时区解耦。
- 无密码公开数据库、全局 CORS、浏览器 API 密钥配置均不采用。当前接口无登录，只允许 loopback，本版本不用于公开服务器。
- 当前阶段只实现 M0/M1。AI、周复盘、资讯和生产部署尚未实现；页面明确标注未接入能力。

来源：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[React 版本](https://react.dev/versions)、[PostgreSQL 支持政策](https://www.postgresql.org/support/versioning/)。
