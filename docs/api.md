# GrowthHub 接口契约

前缀 /api/v1；JSON、camelCase；错误为 code/message/fieldErrors/requestId。

| 方法与路径 | 内容 |
| --- | --- |
| GET /dashboard?date=YYYY-MM-DD | 当日任务、最近五条记录、本周分钟、逾期任务数；默认上海日期 |
| GET/POST /goals | 全部目标列表、创建；priority 1–3 |
| PATCH /goals/{id} | 完整可编辑目标字段 + version；status ACTIVE/ARCHIVED |
| GET /tasks?date=&status=&page=0&size=20 | 分页过滤，最大 size=100 |
| POST /tasks | title、scheduledDate、estimateMinutes 必填；可附 goalId、priority、detail、acceptanceCriteria |
| PATCH /tasks/{id} | version 必填，其余部分更新；clearGoal=true 清除目标；status TODO/IN_PROGRESS/DONE/CANCELLED |
| GET /study-logs?from=&to=&page=0&size=20 | 日期范围分页 |
| GET /study-logs/{id} | 日志详情 |
| POST /study-logs | studyDate、content、durationMinutes 必填；可附 achievements、blockers、taskIds |
| PATCH /study-logs/{id} | {version,data:{同创建字段}}，替换日志可编辑内容和关联 |
| GET /actuator/health | 健康检查，不含数据库凭据 |

任务分钟 1–1440，日志分钟 0–1440。目标标题、任务标题 1–200 字符；日志原文上限 50000 字符。日志允许同日多条。改 DONE 时填 completedAt，重开时清空。

状态码：201 创建、200 查询/更新、400 输入不合法、404 不存在、409 版本/约束冲突。归档目标保留历史关联，不能再创建新关联任务。

当前有意不做硬删除，使用归档或取消。AI 分析、设置与建议接口已在 M2 实现，详见 [AI 契约](ai-contract.md)。数据导出接口仍未实现。

## 个人数据导出

`GET /api/v1/exports` 返回 JSON 附件、no-store 缓存策略。包含八张业务表的一致快照、schemaVersion、exportedAt 和 counts。大小/条数超限返回 413 EXPORT_TOO_LARGE；详见 [M3 试用与导出](m3-trial.md)。提供商配置见 [主流模型配置](model-providers.md)。

## 周计划与复盘

周一开始的事实汇总、计划编辑归档、复盘草稿/刷新/确认见 [周工作台 API](weekly-workspace.md)。导出 schemaVersion 已升级为 growthhub-export-v2，增加 weekly_plan/weekly_review，原有八张表保持。

## 完整初版增量

成果、周归属、AI 周草稿、资讯、登录与导出 v3 的接口契约见 [剩余功能说明](remaining-features.md)，部署与安全会话见 [运行手册](deployment.md)。计划范围与外部待验收项见 [逐项核对](plan-audit.md)。
