# Contract: /api/v1/schedules 四端点

> 009 双信封契约延续：成功 = `ApiResponse`（`code=0` + `data`），错误 = `ErrorResponse`（`errorCode` + `message`）单出口（GlobalExceptionHandler）。时间字段一律 UTC ISO-8601；`zone` 字段随行，展示层按 zone 转本地（Clarifications 2026-09-09）。

## 1. GET /api/v1/schedules — 任务列表

**成功 200**：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "taskId": "weather-8am",
      "profileName": "weather-agent",
      "cron": "0 0 8 * * *",
      "zone": "Asia/Shanghai",
      "message": "生成今日天气和穿搭建议",
      "enabled": true,
      "nextRunAt": "2026-09-10T00:00:00Z",
      "lastRunAt": "2026-09-09T00:00:00Z",
      "lastStatus": "success",
      "runCount": 3
    }
  ],
  "timestamp": "2026-09-09T08:00:00Z"
}
```

- `data` = `ScheduledTaskView` 数组（无任务时为空数组，非 null；按 `taskId` 升序，稳定可测——S5 analyze F3）
- 停用任务 `enabled=false` 且 `nextRunAt` 保留原值（不置空——Clarifications 2026-09-09）

## 2. GET /api/v1/schedules/{id}/executions — 执行历史

**成功 200**：`data` = `TaskExecutionView` 数组（按 `id` 降序，最新在前；无历史时为空数组）：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 42,
      "taskId": "weather-8am",
      "sessionId": "scheduler|scheduler|weather-agent",
      "startedAt": "2026-09-09T00:00:00Z",
      "success": true,
      "errorMessage": null,
      "durationMs": 8213
    }
  ],
  "timestamp": "2026-09-09T08:00:00Z"
}
```

**404**（任务不存在）：

```json
{ "errorCode": 404, "message": "定时任务不存在: xxx", "timestamp": "..." }
```

## 3. POST /api/v1/schedules/{id}/run — 立即执行

- 语义：同步等待执行完成（⑦b）；无视 enabled；与到点触发同锁同入口（⑦a，并发不双跑）
- 请求体：无（空 body 或空 JSON 均可，不消费请求体）
- **成功 200**：`data` = 本次 `TaskExecutionView`（与 executions 历史条目同形状——Clarifications 2026-09-09）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 43,
    "taskId": "weather-8am",
    "sessionId": "scheduler|scheduler|weather-agent",
    "startedAt": "2026-09-09T08:00:00Z",
    "success": true,
    "errorMessage": null,
    "durationMs": 7341
  },
  "timestamp": "2026-09-09T08:00:08Z"
}
```

- 注意：执行**失败**（任务体内部失败）仍是 **200 + success=false + errorMessage**——失败是任务结果，不是 API 错误
- **504**（同步等待超 60s，任务体继续后台跑完、审计照常落账；复用 009 `AgentTimeoutException` 单出口）：

```json
{ "errorCode": 504, "message": "Agent 调用超时（60 秒上限）；任务体继续后台跑完、审计照常落账", "timestamp": "..." }
```

- **404**：任务不存在（同上）
- 幂等性：每次调用都执行一次（无去重），run_count 每次 +1

## 4. PUT /api/v1/schedules/{id} — 启用/停用

- 请求体：

```json
{ "enabled": false }
```

- **成功 200**：`data` = 更新后的 `ScheduledTaskView`
- **400**（请求体缺失 `enabled` 布尔字段）：

```json
{ "errorCode": 400, "message": "请求参数非法：enabled 必填", "timestamp": "..." }
```

- **404**：任务不存在
- 语义：仅改 enabled（setEnabled）；`next_run_at` 保留原值不动（Clarifications 2026-09-09）；定义类字段（cron/message/zone）不接受修改——请求体多余字段忽略（29/30 节才有定义 CRUD）

## 错误信封单出口（009 契约不变）

| 场景 | HTTP | errorCode | 处理 |
|------|------|-----------|------|
| 任务不存在 | 404 | 404 | `ResourceNotFoundException`（复用 009） |
| enabled 字段缺失/非法 | 400 | 400 | `InvalidRequestException`（复用 009） |
| 同步等待超 60s | 504 | 504 | `AgentTimeoutException`（复用 009，R5） |
| 其他异常 | 500 | 500 | GlobalExceptionHandler 兜底 |

## 消费方

- 管理台「定时任务」页（本 feature 前端，POST/PUT 第一个写操作页）
- 管理台之外的工具 / 业务系统（GET 两个读端点与 009 五页同性质）
- 29/30 节 Agent 管理端点复用 `ScheduledTaskStore`（本节六方法，不预留空壳——需求文档「明确不做」）
