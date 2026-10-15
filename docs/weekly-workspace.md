# M4 第一个切片：周计划与事实复盘

本轮交付周主题、整周预算、周统计、手工复盘草稿与确认、来源快照。入口 /weekly。沿用原有任务、日志和 AI 教练，不新增模型调用。

## 使用

1. 选择一周（任意日期均归到当周周一），建立周计划，填写主题、可用分钟和预期成果。
2. 通过原有任务日期安排这一周，日志按实际学习日期保存。周预算为规划参考，超出时提示，不自动搬移或取消任务。
3. 建立复盘草稿，读取保存的日志原文、成果和阻塞，写下复盘内容并保存。
4. 如果修改或补写来源，草稿标为来源已变化；先保存文字，再刷新来源，刷新不会覆盖已保存的复盘文字。
5. 核对后确认。确认后只读，不生成任务；后续补记仍可查看，已确认的历史快照保持不变。

没有记录不代表没有学习，不据此推断能力。没有日志的休息周也可建立计划、写复盘和确认。

## 统计口径

- 周一至周日，业务时区来自 app.timezone（默认 Asia/Shanghai）。日期与服务器 UTC 时区分离。
- 日志实际分钟仅累计 studyDate 落在本周的日志，不再加任务预计时长。
- 排期任务按 scheduledDate 归周，取消任务展示但不计预计时长和有效排期数量。
- “本周排期当前已完成”反映这些任务的当前状态，不能当成历史周完成率。
- “完成时间落在本周”按 completedAt 的上海时间周边界筛选，包含排期在其他周的任务；任务重开后完成时间清空，不计入当前完成统计。
- 日志关联的跨周任务单独保留，可能与排期/完成列表重复，不重复累计实际分钟。

## 存储与并发

Flyway V3 新增 weekly_plan 和 weekly_review。每个周一分别至多一份计划和一份复盘。计划支持编辑、归档和恢复，周起始日期固定，修改需 version。前端发生版本冲突时保留本地未保存的复盘文字，可显式载入服务端已保存内容。

复盘包含原文 source_snapshot 和内部 SHA-256 source_hash。读取与冻结来源使用 PostgreSQL REPEATABLE READ。日志关联 ID 在哈希前排序，防止集合迭代顺序造成误判。来源包括新增日志/任务、计划版本、日志版本、排期任务、实际完成任务和日志关联任务。

确认锁定复盘行，检查 version、非空文字和来源哈希。已确认时重试返回同一份复盘。并发序列化或锁冲突返回 409 CONCURRENT_UPDATE，刷新重试；不会透传数据库异常。重复建周计划/复盘由唯一约束兜底，失败不会覆盖已有内容。

每类周日志/排期/完成任务最多 500 条，关联任务最多 1000 条，快照最多 200 万字符；超出返回 413 WEEK_TOO_LARGE，使用个人数据导出或数据库备份。不会静默裁剪依据。

## API

| 方法与路径 | 内容 |
| --- | --- |
| GET /api/v1/weeks?date=YYYY-MM-DD | 归一化为周一，返回 facts 和 review（可空），省略日期默认业务时区今日 |
| POST /api/v1/weekly-plans | weekStart（周一）、theme、budgetMinutes（0..10080）、notes |
| PATCH /api/v1/weekly-plans/{id} | version、theme、budgetMinutes、notes、status（ACTIVE/ARCHIVED） |
| POST /api/v1/reviews/weekly | weekStart（周一），冻结来源建立空草稿 |
| PATCH /api/v1/reviews/weekly/{id} | version、finalContent（最多 30000 字符，原文保存） |
| POST /api/v1/reviews/weekly/{id}/refresh | version；只更新来源快照，保留文字 |
| POST /api/v1/reviews/weekly/{id}/confirm | version；确认后只读 |

GET weeks 的 facts 含 weekStart/weekEnd/timezone、plan、summary、logs、scheduledTasks、completedDuringWeek、linkedTasks。review 含 id/version、finalContent/status、confirmedAt、stale、snapshot。

JSON 导出升级为 growthhub-export-v2，增加 weekly_plan/weekly_review，总计十张业务表；包含复盘原文和来源快照，排除内部 source_hash。原导出 v1 的八张表键名保持不变。导出大小和数量限制不变，完整恢复仍使用数据库备份。

## 后续切片

独立成果记录、周任务显式归属/分配以及周复盘 AI 草稿尚未实现，本轮没有给任务新增 weekly_plan_id；目前按实际排期归周，避免引入未交付的任务分配入口。后续显式分配时再迁移该字段并验证跨周调整规则。AI 草稿仍须使用真实日期和任务证据、人工编辑确认；不得复用每日分析接口伪装成周分析。

M3 的真实模型联调和七天观察仍独立待办，不因开发 M4 而标记完成。本轮是 M4 的第一个可验收切片，不宣称整个 M4 完成。

完整初版更新：此前“后续切片”中的成果、周归属和 AI 周复盘已在本轮实现。统计增加学习/开发分类，使用可保存业务时区；新增任务 weekly_plan_id。接口与保护规则以 remaining-features.md 为准，导出已升级 v3。
