# 初版计划剩余功能接口与边界

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

## 资讯

两个固定官方 Atom Release 来源：Spring Boot 和 pgvector；管理员环境变量可覆盖采集地址用于本机测试，网页不能指定任意抓取 URL。真实源本轮实际 HTTP 200。

- GET /api/v1/feed/sources；POST /api/v1/feed/refresh（有短暂冷却）
- GET /api/v1/feed/items?source=&favorite=false&page=0&size=20
- PATCH /api/v1/feed/items/{id}：version/favorite
- GET /api/v1/feed/candidates
- POST /api/v1/feed/items/{id}/candidate：title/detail/acceptanceCriteria/estimateMinutes/scheduledDate
- PATCH /api/v1/feed/candidates/{id}：version/data
- POST /api/v1/feed/candidates/{id}/confirm 或 reject：version

URL 规范化、外部 ID 与 URL 唯一约束去重，内容指纹仅在变化时更新；收藏保留。禁用 DTD/外部实体，正文只作文字展示。每源响应最多 2 MB、总读取最多 12 秒，每次最多 100 条，单条正文最多 20000 字符；官方原文链接始终可查看。采集失败保留旧资讯，不中断手工任务/日志。

候选先保存再编辑和显式确认，确认锁定行并事务创建一个正式任务；重复/并发确认返回同一个 taskId。一个条目对应一份候选，确认/拒绝后只读，不把资讯直接变成正式计划。

## 设置、认证和导出

PATCH /api/v1/settings 可选 timezone（有效 IANA 名称），历史日期不随时区切换改变。日志 activityType 为 LEARNING/DEVELOPMENT/REST，未填写兼容默认学习；休息时长为 0。周统计同时返回 learningMinutes/developmentMinutes，不与任务预计分钟相加。

GET /api/v1/auth/session 返回认证启用状态、会话状态、业务时区与 CSRF token。启用认证时 POST /auth/login 使用表单 username/password 并携带 X-CSRF-TOKEN；POST /auth/logout 同样校验 CSRF。业务接口未登录 401，非法 CSRF 403，登录限流 429；详见 deployment.md。

导出升级 growthhub-export-v3，覆盖 15 张业务表；保留成果、来源、候选、AI 作业和周复盘快照，排除密钥、请求幂等键和内部哈希。大小/条数限制不变，不提供未经设计的 JSON 导入。完整恢复使用数据库备份。
