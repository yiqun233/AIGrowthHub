# 主流模型提供商配置

更新：2026-10-15。支持文本模型的学习分析与结构化建议；不包含图片、语音、视频和工具调用。模型 ID 必须由所选服务商账号提供，不把营销名称或网页会员当成 API 权限。

在项目私有 `.env` 配置下表前缀对应的 `PREFIX_API_KEY` 和 `PREFIX_MODEL`，然后执行 `python3 scripts/dev.py stop`、`python3 scripts/dev.py start`。密钥不进入网页、数据库和个人数据导出。设置页的“已配置”表示配置存在，实际模型权限、余额与网络要以首次真实调用为准。不要把密钥提交到 Git 或发送到聊天中。

| 选项 / 环境变量前缀 | 默认 API 基地址 | 协议 / 默认输出模式 |
| --- | --- | --- |
| OpenAI / OPENAI | https://api.openai.com/v1 | Chat Completions / JSON_SCHEMA |
| DeepSeek / DEEPSEEK | https://api.deepseek.com | Chat Completions / JSON_OBJECT |
| Claude / ANTHROPIC | https://api.anthropic.com/v1 | Messages / TEXT |
| Gemini / GEMINI | https://generativelanguage.googleapis.com/v1beta | generateContent / JSON_OBJECT |
| 通义千问 / QWEN | https://dashscope.aliyuncs.com/compatible-mode/v1 | OpenAI 兼容 / JSON_OBJECT |
| 智谱 / ZHIPU | https://open.bigmodel.cn/api/paas/v4 | OpenAI 兼容 / JSON_OBJECT |
| Kimi / MOONSHOT | https://api.moonshot.cn/v1 | OpenAI 兼容 / JSON_OBJECT |
| 豆包 / DOUBAO | https://ark.cn-beijing.volces.com/api/v3 | OpenAI 兼容 / JSON_OBJECT |
| OpenRouter / OPENROUTER | https://openrouter.ai/api/v1 | OpenAI 兼容 / JSON_OBJECT |
| 自定义 / CUSTOM | 必填 CUSTOM_BASE_URL | OpenAI 兼容 / TEXT |

每家都能通过 `PREFIX_BASE_URL` 覆盖基地址（应包含 `/v1` 等版本路径，不能包含 `/chat/completions`、`/messages`、密钥或查询参数）。生产提供商必须 HTTPS；HTTP 只允许回环地址用于本机测试。不跟随重定向。百炼地区、Kimi 国内外账号、OpenRouter 转发和企业网关须使用对应账号的地址。

示例（将尖括号替换为账号真实值）：

```dotenv
QWEN_API_KEY=<服务端密钥>
QWEN_MODEL=<控制台可用的模型ID>
# 可选：地区对应的完整兼容 API 基地址
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

豆包 `DOUBAO_MODEL` 可以是控制台允许调用的模型 ID 或推理接入点 ID；OpenRouter 模型 ID 通常带提供商前缀。Gemini 填模型 ID，不加 `models/`。模型名称保留为自由输入，以便使用账号新开放的模型，程序不宣称已验证每个模型。

## 模型兼容开关

`PREFIX_OUTPUT_MODE` 支持大写 `TEXT`、`JSON_OBJECT`、`JSON_SCHEMA`。OpenAI 兼容接口会发送对应的 response_format；模型不支持时可显式改为 TEXT，仍由提示词要求 JSON，再由服务端执行完整结构、证据、关联和预算校验。不会因参数错误自动降级并额外付费重试。

Claude 默认只请求纯文本 JSON；支持原生结构化输出的模型可设 `ANTHROPIC_OUTPUT_MODE=JSON_SCHEMA`，使用 output_config.format。Claude 的 JSON_OBJECT 与 TEXT 均采用提示词 JSON。Gemini 的 JSON_OBJECT/JSON_SCHEMA 均启用 JSON MIME 模式，完整 schema 在服务端验证，不宣称原生严格 schema；TEXT 关闭 MIME 限制。Claude 忽略 thinking 块，Gemini 忽略 thought 部分，只读取最终文本。两者均拒绝未正常结束的结果。

OpenAI 兼容协议可设置 `PREFIX_TOKEN_PARAMETER=max_tokens` 或 `max_completion_tokens`；OpenAI 与豆包默认后者，其余默认前者。此开关不影响 Claude / Gemini 原生 token 参数。默认输出上限 4096 token，复杂推理模型可能在预算内没有足够最终文本，应选择适合结构化日常分析的模型。

所有协议共用原有异步、两次有界请求、总超时、次数限制、证据校验和人工确认流程。模型返回的错误正文不会透传到网页。

## 官方资料

- [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
- [Anthropic Messages](https://platform.claude.com/docs/en/api/messages/create)
- [Gemini generateContent](https://ai.google.dev/api/generate-content)
- [百炼 OpenAI 兼容接口](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope)
- [智谱 API](https://docs.bigmodel.cn/cn/api/introduction)
- [Kimi API](https://platform.kimi.com/docs/api/chat)
- [火山方舟 Chat API](https://docs.volcengine.com/docs/ark/chat-api?lang=zh)
- [OpenRouter](https://openrouter.ai/docs/quickstart)
