# GrowthHub 实施进度

更新日期：2026-10-15。

## 已完成 M0：环境和骨架

- 确认唯一源码目录 /home/yi/projects/GrowthHub；项目名 GrowthHub。
- 安装 Ubuntu 原生 Java 21、Maven 3.9.12、PostgreSQL 18.6；复用已有 Volta Node 24.18.1。
- 建立 Spring Boot 4.1.1、React SPA、Flyway、Maven Wrapper、可选数据库 Compose 和 CI 配置。
- 初始 M0/M1 代码已提交到 main（36f521d）并推送至 yiqun233/AIGrowthHub；.env、缓存、构建产物和备份均被忽略。

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

M2 已完成；下一里程碑 M3 为 7 天试用、数据导出及体验改进。真实提供商联调前再配置 API 密钥，无需把密钥发到聊天中。

尚未交付：周计划/复盘、资讯、数据导出接口、公开服务器部署与认证。当前设置页只显示实际状态，不提供虚假的可用按钮。

## 前端锁定版本

- @ant-design/icons：6.3.4
- @tanstack/react-query：5.104.2
- antd：6.6.6
- dayjs：1.11.23
- react：19.3.1
- react-dom：19.3.1
- react-router-dom：7.18.4

Windows 原生 HTTP 校验：明确 IPv4 地址 http://127.0.0.1:5173 返回 200。完整验收已通过，无需再调整全局网络设置。


## 已完成 M2：AI 学习教练

- OpenAI / DeepSeek 适配、JSON 输出与证据/关联/预算校验。
- 异步状态、持久化快照、两次有界请求、上海自然日次数上限与安全错误信息。
- 用户同意发送上下文后主动触发分析；结果不直接写任务。
- 草稿编辑、拒绝、来源失效、新建议替代、事务幂等确认；已有任务沿用 ID 重新排期。
- AI 教练页、日志分析入口、设置页真实配置状态、默认模型/时间与偏好。
- 滞留任务过期和进程崩溃恢复；原手工流程继续可用。
- 日志保存后定位到所选日期，筛选保存在 URL，刷新仍可找回。

M2 验证：4 项 JUnit、31 项真实数据库/API 回归、4 项浏览器测试通过，另通过强制中断测试进程后的重启恢复和无密钥拦截检查。CI 测试脚本已改为从 PATH 定位 Java/npm，避免依赖本机 Volta 路径；远端 CI 仍须后续推送后查看，不能把本地验证冒充远端结果。

本地数据库升级前已备份；Flyway V1/V2 均成功，原目标/任务/日志/关联的逐行哈希一致。本轮开发库没有测试数据写入，测试提供商仅运行在隔离测试环境。

真实提供商尚未联调：本机 OpenAI、DeepSeek 密钥均未配置，页面据实禁用分析提交。配置流程与接口契约见 docs/ai-contract.md。M2 改动本轮尚未提交或推送。


# M2 AI 学习教练契约与运维

更新：2026-10-15。实现为单实例、单用户本机应用；不引入 Agent 框架、向量库、消息队列或额外 Python 业务服务。

## 已实现流程

保存原始日志 → 选择来源日志、提供商、模型、日期和预算 → 明确同意发送上下文 → 异步分析 → 查看来源与推断 → 编辑/拒绝/确认草稿 → 事务创建或调整正式任务。

AI 结果、用户编辑的草稿、最终任务分别保存，不把模型输出直接写成正式计划。确认后再次提交返回原来的任务 ID；拒绝、过期版本和已被新建议替代的草稿不能确认。目标、任务、日志或学习偏好发生变化时，相关草稿会在读取或确认时判为失效；重新生成时使用新的事实快照。

## 配置真实提供商

在 `/home/yi/projects/GrowthHub/.env` 配置对应服务端密钥，例如填入自己的 `OPENAI_API_KEY` 或 `DEEPSEEK_API_KEY`。不要把真实密钥写入 `.env.example` 或 Git。

可选配置：

| 变量 | 默认/含义 |
| --- | --- |
| OPENAI_BASE_URL | https://api.openai.com/v1，自动拼接 /chat/completions |
| OPENAI_MODEL | 留空时在设置页或本次分析表单中填写账号可用模型 |
| DEEPSEEK_BASE_URL | https://api.deepseek.com，自动拼接 /chat/completions |
| DEEPSEEK_MODEL | 同上，不假定每个账号有相同模型权限 |
| AI_TIMEOUT_SECONDS | 90 秒，配置范围钳制 1–180 秒 |
| AI_DAILY_LIMIT | 20 次分析/上海自然日，配置范围 1–10000 |

设置页保存的默认模型优先于环境中的默认模型；分析表单仍可指定本次模型。提供商切换时会重置为该提供商对应模型配置。非敏感偏好保存在 app_setting 单例行，服务端只返回密钥是否配置，不返回密钥内容、请求鉴权头或提供商错误响应正文。

配置后重启：

```bash
cd /home/yi/projects/GrowthHub
python3 scripts/dev.py stop
python3 scripts/dev.py start
```

访问 `http://127.0.0.1:5173/settings` 设置默认提供商、模型名称、每日分钟数和学习偏好；从日志的“AI 分析”或侧栏“AI 教练”进入使用。页面显示“已配置密钥”表示服务端读到了配置，不代表已经验证模型权限、网络或账号额度。

## 提供商请求区别

- OpenAI：Chat Completions，response_format=json_schema、strict=true，使用 max_completion_tokens。
- DeepSeek：Chat Completions，response_format=json_object，提示中明确要求 JSON，使用 max_tokens。
- 共用 AiProvider 接口与 HTTP 传输；根据提供商应用不同请求契约，分别由隔离 HTTP 测试核验。
- 管理员提供的基地址仅允许 HTTPS 或明确的本机 HTTP 测试地址；网页不能修改基地址。HTTP 客户端不自动跟随重定向，避免鉴权头被转发。
- 当前实现不自动读取 WSL 的 HTTP_PROXY/HTTPS_PROXY 环境变量。如果真实 OpenAI 调用受网络影响，应先确认 WSL 网络可达；需要显式代理支持时再配置实现，不声称已经验证中国网络环境下的连通性。

## 上下文与输出边界

源日志原文完整保存。源日志正文、成果与阻塞合计超过 24000 字符时拒绝分析，不丢弃原文。读取源日志之前 14 天范围的最近最多 14 条辅助日志；太长的辅助日志可省略，并在快照记录省略数量。目标最多 30 个 ACTIVE；优先纳入来源关联任务，再纳入最多 50 个未完成任务；目标日既有任务最多 50 条。最终快照限制 60000 字符，超过时缩减辅助日志或提示用户整理。

这些限制是字符和记录数边界，不冒充精确 token 计数。模型上下文窗口不同仍可能拒绝请求；输入快照、prompt/schema 版本用于后续诊断。

输出字段统一采用 API 的 camelCase：summary、learnedTopics、evidence、openQuestions、masteryAssessment、tomorrowTasks、planAdjustmentReason。任务包括 title、reason、estimatedMinutes、acceptanceCriteria、goalId、existingTaskId；服务端赋予稳定 itemId。

结构来源为 `backend/src/main/resources/ai/coach-schema.json`。服务端显式验证必需字段、未知字段、类型、数组数量与字符串长度，再检查证据、关联和业务预算，不仅依赖提供商 JSON 模式。此实现使用与固定 Schema 对应的显式校验器，并未引入通用 JSON Schema 引擎。

- evidence 至少一条，日志 ID 必须来自快照，quote 必须能在对应原文/成果/阻塞中找到。
- masteryAssessment 在页面明确标为模型推断，不是能力认证。
- 明日建议允许 0–3 项；0 项可用于休息，正时长任务必须带验收产出。
- 确认时计入目标日已有、未取消任务的预计分钟；被重新安排的原任务只计算一次。
- existingTaskId 必须来自输入任务，不能指向已完成/取消任务；确认沿用原 ID，不复制任务。
- 任务和草稿的修改都有版本检查。用户编辑草稿可删减、增补或改日期；预算保持本次分析输入值，需要更大预算时重新生成。

## 生命周期与幂等

POST /ai/analyses 使用调用方生成的 Idempotency-Key，键 8–128 位且仅包含字母/数字/下划线/连字符。同键同输入返回原分析；同键不同输入返回 409。失败后明确重新提交使用新键，网络响应丢失后的重试沿用原键。

数据库以部分唯一索引限制一条 PENDING/RUNNING；app_setting 行锁串行化创建与确认。调度线程领取持久化 PENDING，提交 RUNNING 后在事务外调用 HTTP；不会持有数据库事务等待模型。

每次分析整体最多两次请求：认证、常规 4xx 不重试；429 与临时 5xx 在剩余总时限内有界重试；结构或业务校验失败允许一次纠正。Retry-After 超过剩余时间时不继续等待。默认总时限 90 秒，单次输出上限 4096 tokens。

服务重启将遗留 RUNNING 标记为 PROCESS_RESTARTED，PENDING 继续领取；执行异常遗留的 RUNNING 超过时限加 10 秒后标为 ANALYSIS_EXPIRED，避免永久阻塞后续分析。结果写回检查状态，不覆盖已经失败的任务。此生命周期按单实例设计，不支持同时运行多个生产后端。

确认用同一事务：校验草稿状态/版本/事实 → 校验当前目标日预算 → 写任务 → 写 suggestion_task 唯一映射 → 更新 CONFIRMED。中途写入失败全部回滚。重复确认直接返回映射；相同事实与来源、同一目标日的新分析成功后替代旧未确认草稿，不在新分析失败时废弃旧草稿。

## 接口

前缀 /api/v1。

| 方法/路径 | 行为 |
| --- | --- |
| GET/PATCH /settings | 查询/保存非敏感设置，PATCH 带 version |
| POST /ai/analyses | 202，sourceLogId、targetDate、budgetMinutes、provider、model、feedback；带 Idempotency-Key |
| GET /ai/analyses?page=&size= | 历史分页，最多 50 条/页 |
| GET /ai/analyses/{id} | 状态、结果、输入快照、尝试次数、用量、耗时与安全错误 |
| GET /plan-suggestions/{id} | 草稿、来源、状态、版本、失效原因、确认任务映射 |
| PATCH /plan-suggestions/{id} | version、targetDate、tasks；仅可编辑有效 DRAFT |
| POST /plan-suggestions/{id}/confirm | {version}，返回固定 taskIds，可安全重复调用 |
| POST /plan-suggestions/{id}/reject | {version}，保留拒绝历史，不改变正式任务 |

所有 AI 分析只在用户主动提交后产生，不自动扫描日志调用模型。页面轮询只查询本地状态，不会再次调用提供商。

## 用量与数据保护

记录 provider/model、尝试次数、耗时和提供商返回的 token 用量；多次成功返回响应的用量相加。拿不到用量时为未知。超时后远端可能仍有计费，调用次数与输出上限不是绝对金额封顶。本阶段不凭空估算费用，不提供不准确的金额保证。

快照包含个人日志，与原始数据库和备份一样需要保护；系统日志不打印快照、鉴权头或远端错误正文。AI 输出按普通文本渲染，外部笔记不能修改系统规则或执行代码。未配置 AI 或分析失败不影响原有手工功能。

## 已完成验证与未验证项

- 4 项 Java 单元测试：每日上限、同键不同输入、缺少密钥、滞留分析过期。
- 31 项真实 PostgreSQL/API 测试（原 10 项 + M2 21 项）：双适配器、请求幂等、并发确认、重试与超时、证据/类型/关联检查、零任务、已有任务排期、预算、草稿版本/事实失效、拒绝/替代、数据库第二次插入失败后的完整回滚等。
- 4 项 Chromium 验收：原手工功能、AI 生成→编辑→确认→正式任务、失败恢复、未配置状态与手机宽度。
- 独立测试进程被强制结束后重新启动，分析转为 PROCESS_RESTARTED，原始日志保留；空密钥配置被正确拒绝。
- 测试使用本机 HTTP 提供商模拟服务，不发送真实用户记录给外部 API，不产生真实 API 费用。
- 真实 OpenAI/DeepSeek 权限、网络、建议质量与费用尚未验证，需要用户在本机配置密钥后用匿名化记录联调。

运行 `python3 scripts/test.py` 会在启动前清空独立 growthhub_test 的业务测试数据，保留 Flyway 历史和设置单例；开发库 growthhub 不会被清理。测试占用 18080（后端）、15173（前端）和 18889（模拟提供商）；检测到已有测试后端时拒绝清理。`--api-only` 跳过浏览器，仍执行 AI API 与重启恢复检查。

官方接口依据（2026-10-15 查阅）：[OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[DeepSeek JSON Output](https://api-docs.deepseek.com/guides/json_mode/)。
