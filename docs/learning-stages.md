# 学习主题阶段闭环

本功能对应 V1.2。一个学习阶段围绕单个主题，可以跨越多个周计划。它为每日可选记录、周复盘和阶段总结提供共同关联，不要求每日学习或打卡。

## 使用规则

- 页面内“使用说明”提供面向使用者的闭环图解和键盘操作提示；此文档维护阶段生命周期、数据规则与接口契约。
- 阶段记录主题、预期结果、可观察验收条件、开始日期和可选目标日期。目标日期是参考，不用于自动判定完成。
- 一个时间最多一个 ACTIVE 阶段。可暂停后开始另一个阶段；恢复前需先暂停其他 ACTIVE 阶段。
- 任务、日志、周计划和独立成果均可关联阶段；已有历史数据在迁移后保持未关联。
- 休息、没学习或没有记录不构成失败。每日记录、每日 AI 建议和周复盘仍可选。
- 阶段复盘可以手写，也可以主动生成 AI 草稿。阶段结果为达成、部分达成、调整范围或放弃。只有用户确认复盘并选择结果后阶段才结束。
- AI 不会自动创建任务、结束阶段或开启下一阶段。能力判断属于模型推断；证据引用真实关联记录、任务、成果或周复盘。

## 生命周期

`ACTIVE ↔ PAUSED → CLOSED`。CLOSED 阶段不可恢复。结束需要阶段复盘 `DRAFT → CONFIRMED`，并在同一事务中锁定复盘、确认来源仍然新鲜、写入结果和关闭阶段。重复确认返回已确认内容。

阶段复盘建立时冻结阶段信息、关联日志、任务、成果、周计划和周复盘。草稿保存/刷新/确认使用版本号；来源修改会使草稿标为 stale，需刷新并重新核对。刷新只替换来源快照，不覆盖已保存的文字。确认后保留原快照，后续输入不改写历史。

AI 阶段总结作为独立 `STAGE_REVIEW` AI 作业，沿用统一 AI 预算、超时、恢复、请求幂等、人工同意和结构化证据校验。调用上限与提供商用量不构成金额保证。上下文超过 60000 字符时拒绝发送，原文保持不变。AI 结果可以显式采用到可编辑草稿；采用不会确认复盘或修改任务。

## API

前缀 `/api/v1`，JSON 字段使用 camelCase。

| 方法与路径 | 行为 |
| --- | --- |
| GET/POST `/stages` | 查询阶段；创建主题、预期结果、验收条件、开始日期和可选目标日期 |
| GET `/stages/{id}` | 阶段详情、阶段关联事实与复盘 |
| PATCH `/stages/{id}` | 编辑阶段信息，必须带 `version` |
| POST `/stages/{id}/pause` | 暂停，带 `version` |
| POST `/stages/{id}/resume` | 恢复，带 `version`；存在其他活动阶段时返回冲突 |
| POST `/stages/{id}/reviews` | 冻结当前阶段来源，创建唯一复盘草稿 |
| GET/PATCH `/stage-reviews/{id}` | 查询或编辑复盘文字；更新带 `version` |
| POST `/stage-reviews/{id}/refresh` | 刷新来源快照，不覆盖复盘文字 |
| POST `/stage-reviews/{id}/confirm` | `{version,outcome}`；确认并关闭阶段 |
| POST `/stage-reviews/{id}/generate` | 主动提交 AI 阶段总结；需 `Idempotency-Key`、提供商、模型和 `consent=true` |
| POST `/stage-reviews/{id}/apply-ai` | `{version,workId}`；将有效 AI 结果采用为可编辑复盘草稿 |
| GET `/ai/works?kind=STAGE_REVIEW&targetId={reviewId}` | 查询阶段 AI 作业历史 |

`learning_task`、`study_log`、`weekly_plan`、`achievement` 增加可空 `learning_stage_id`。日期、时间口径沿用现有 `Asia/Shanghai` 业务时区。阶段页面只汇总已关联数据，不按日期推断关联。

## 数据导出

JSON 导出升级为 `growthhub-export-v4`，覆盖 17 张业务表，包括 `learning_stage` 和 `learning_stage_review` 及关联字段。导出排除内部来源哈希；不提供 JSON 导入。完整恢复仍使用数据库备份。Flyway 通过 V8 迁移，旧数据不会自动归到某个阶段。

## 当前验证状态

实现状态及本轮可复核证据见 [plan-audit.md](plan-audit.md) 和 [progress.md](progress.md)。文档中的接口和状态描述来自本轮源码；真实模型联调、测试库/API/浏览器验收不得由代码存在推断。
