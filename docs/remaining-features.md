# 完整功能接口与边界

## 成果

GET/POST /api/v1/achievements；PATCH /api/v1/achievements/{id}。

创建字段：title、description、url（空或 HTTP/HTTPS）、kind（PROJECT/CODE/ARTICLE/CERTIFICATE/OTHER）、achievementDate、goalId/taskId（可空）。修改字段为 version、data（完整创建字段）、archived。列表支持 archived、from/to、page/size，归档保留历史引用。日志原有 achievements 文本不迁移、不删除。

## 周任务归属

GET /api/v1/weekly-plans 返回最近 200 份周计划。PATCH /api/v1/tasks/{id}/weekly-plan：version、weeklyPlanId（null 解除归属）、scheduledDate（可选）。归属到有效计划时，日期必须在该周；跨周调整可在一次请求中指定新归属与日期。数据库触发器保护所有写入路径，包括手工任务编辑和 AI 确认；失败事务整体回滚，不默默解除归属。归档计划不能接受新分配，原有关联保留。

## 周复盘 AI / 资讯摘要

- POST /api/v1/reviews/weekly/{id}/generate
- POST /api/v1/feed/items/{id}/summarize
- GET /api/v1/ai/works/{id}
- GET /api/v1/ai/works?kind=WEEKLY_REVIEW|FEED_SUMMARY&targetId=UUID
- POST /api/v1/reviews/weekly/{id}/apply-ai

生成需 Idempotency-Key 和 version/provider/model/consent=true。任务异步持久化，共享每日 AI 的单活动限制、自然日次数上限，单次最多两次请求、总超时、重启恢复与过期扫描。上下文最多 60000 字符，超限返回 413，原文保留。

输出为 summary、evidence（sourceId/quote）、nextSteps、limitations；严格检查字段类型、长度、真实来源 ID 和原文引用。周复盘引用真实日志或任务，空周明确缺少依据；资讯只总结传入的发布内容。提示词版本为 weekly-v1/feed-v1，结构 work-v1，记录 provider/model、耗时和可获取 token；费用没有计价表，不推算精确价格。

采用周草稿需 workId/version，检查对象、状态和来源哈希；明确采用后替换草稿文字，仍需人工编辑确认。生成本身不覆盖文字、不改任务。确认后只读，模型失败保留原文和已有文字。前端保留异步历史、错误和输入快照。

## 多平台资讯

订阅体验：单个主页链接粘贴后按域名预选平台，仍可手动更改；批量模式每行一个主页链接，先展示识别结果，再逐条保存并反馈失败项。订阅列表显示来源收录数、最近更新时间和异常状态；拉取资讯需手动点击刷新。前端仅自动识别常见域名，具体主页路径由服务端校验，外部平台可用性仍需实测。

2026-10-15 用户确认的目标平台：雪球、集思录、淘股吧、东方财富、同花顺、韭圈儿、有知有行、微信公众号、知乎、微博、小红书、掘金、CSDN、V2EX。订阅以主页/栏目链接创建，微信公众号也可输入公众号名称；服务端按平台映射到直接 RSS、RSSHub 或页面抓取器，浏览器不能指定任意抓取主机。`RSSHUB_URL` 可设置为自建或可信 RSSHub 实例，缺省为公共实例。微信公众号等来源需要 RSSHub 侧提供凭据/配置；部分平台有严格反爬、会话或浏览器环境要求。可选来源 Cookie 需用户主动设置并配置服务端 `FEED_COOKIE_ENCRYPTION_KEY`；部分受限内容仍需在可信 RSSHub 实例侧配置。

- GET /api/v1/feed/platforms：平台目录与抓取说明
- GET/POST /api/v1/feed/sources：来源列表、添加主页订阅
- DELETE /api/v1/feed/sources/{id}：停用订阅，保留历史资讯
- PUT/DELETE /api/v1/feed/sources/{id}/cookie：设置/清除该抓取来源的加密 Cookie；仅页面抓取适配器支持，不返回 Cookie 明文
- POST /api/v1/feed/refresh（有短暂冷却）
- GET /api/v1/feed/items?source=&favorite=false&page=0&size=20
- PATCH /api/v1/feed/items/{id}：version/favorite
- GET /api/v1/feed/candidates
- POST /api/v1/feed/items/{id}/candidate：title/detail/acceptanceCriteria/estimateMinutes/scheduledDate
- PATCH /api/v1/feed/candidates/{id}：version/data
- POST /api/v1/feed/candidates/{id}/confirm 或 reject：version

URL 规范化、外部 ID 与 URL 唯一约束去重，内容指纹仅在变化时更新；收藏保留。禁用 DTD/外部实体，正文只作文字展示。每源响应最多 2 MB、总读取最多 12 秒，每次最多 100 条，单条正文最多 20000 字符；原文链接始终可查看。采集失败保留旧资讯，不中断手工任务/日志。V7 增加订阅字段与独立加密凭据表，数据导出不包含该表；14 个平台的代码映射已加入，真实抓取、RSSHub 侧凭据配置与反爬适配尚未逐项验证，不能根据代码映射标记通过。

候选先保存再编辑和显式确认，确认锁定行并事务创建一个正式任务；重复/并发确认返回同一个 taskId。一个条目对应一份候选，确认/拒绝后只读，不把资讯直接变成正式计划。

## 设置、认证和导出

PATCH /api/v1/settings 可选 timezone（有效 IANA 名称），历史日期不随时区切换改变。日志 activityType 为 LEARNING/DEVELOPMENT/REST，未填写兼容默认学习；休息时长为 0。周统计同时返回 learningMinutes/developmentMinutes，不与任务预计分钟相加。

GET /api/v1/auth/session 返回认证启用状态、会话状态、业务时区与 CSRF token。启用认证时 POST /auth/login 使用表单 username/password 并携带 X-CSRF-TOKEN；POST /auth/logout 同样校验 CSRF。业务接口未登录 401，非法 CSRF 403，登录限流 429；详见 deployment.md。

学习主题阶段 API、阶段关联、阶段 AI 作业与阶段快照见 [学习主题阶段闭环](learning-stages.md)。

导出升级 `growthhub-export-v4`，覆盖 17 张业务表；包含学习阶段和阶段复盘，保留成果、来源、候选、AI 作业和周/阶段复盘快照，排除密钥、请求幂等键和内部来源哈希。大小/条数限制不变，不提供 JSON 导入。完整恢复使用数据库备份。
