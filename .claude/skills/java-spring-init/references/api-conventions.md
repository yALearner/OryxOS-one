# REST API 约定（步骤 6 配套）

## 响应信封

统一 `ApiResponse<T>`（若项目已有定义则沿用项目版）：

| 字段 | 类型 | 约定 |
|------|------|------|
| `code` | int | `0` = 成功；非 0 = 错误码，与 HTTP 状态码一致（400/404/500/503），客户端可直接按状态码处理 |
| `message` | string | 人类可读消息 |
| `data` | T | 业务数据；错误时为 `null` |
| `timestamp` | ISO-8601 | 响应时间 |

异常统一由 `GlobalExceptionHandler`（`@RestControllerAdvice`）转换为标准错误体
`ErrorResponse(errorCode, message, timestamp)`，覆盖：
400（参数校验/请求体不可读）、404（NoResourceFoundException/NoHandlerFoundException）、
503（业务抛 `ServiceUnavailableException`）、500（兜底，服务端记录完整堆栈，只回通用文案）。

## 命名与路径

- 资源名词用**复数**：`/api/v1/sessions`、`/api/v1/agents`
- 统一前缀 `/api/v1`；版本演进只加版本段，不破坏旧前缀
- 子资源嵌套不超过两层：`/sessions/{id}/messages`
- 状态码语义：创建 201、删除 204、参数错 400、未找到 404、能力不可用 503

## springdoc 注解约定

- 每个公开端点：`@Operation(summary, description)`；参数 `@Parameter(description, example)`；
  模型 `@Schema(description)`——OpenAPI 文档是给编排平台（Dify/Coze）对接用的，描述必须可读
- OpenAPI 快照门禁：CI 中生成 openapi JSON 后 `git diff --exit-code` 校验，
  端点变更必须同步快照（防止文档与实现漂移）

## 版本兼容

- springdoc 2.8.x ↔ Boot 3.5.x；**springdoc 3.x 只配 Boot 4**（升级 Boot 前不要动 springdoc）
- 验证三件套：`/swagger-ui.html` 可打开、`/v3/api-docs` JSON 可解析、MockMvc 冒烟通过
