# Data Model: 010-scheduler-mgmt

> Phase 1 输出。表结构源 = 需求文档 DDL 骨架（课件 28 节口径，2026-09-09 拍板）；时间字段口径见 spec Clarifications 2026-09-09（存 UTC + 展示按任务 zone 转）。

## 实体一：ScheduledTask（`scheduled_tasks` 表，storage 实体）

| 字段 | 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|------|
| taskId | `task_id` | String | PK，TEXT | = frontmatter schedules 条目的 `id`（拍板 A），此后不变（跨节契约） |
| profileName | `profile_name` | String | NOT NULL | 归属 Profile |
| cron | `cron` | String | NOT NULL | cron 表达式（Spring 六段含秒） |
| zone | `zone` | String | 可空 | 时区 ID；空 = 系统时区（008 口径） |
| message | `message` | String | NOT NULL | 到点发给 Agent 的消息 |
| enabled | `enabled` | Boolean | NOT NULL，默认 true | 管理台开关；停用 = 到点跳过不记历史 |
| nextRunAt | `next_run_at` | Instant | 可空 | 下次触发时刻（UTC，InstantTextConverter） |
| lastRunAt | `last_run_at` | Instant | 可空 | 上次真正执行时刻（UTC） |
| lastStatus | `last_status` | String | 可空 | `success` / `failure`（未跑过为 null） |
| runCount | `run_count` | Integer | NOT NULL，默认 0 | 累计真正执行次数（含 runNow，见状态机） |

## 实体二：TaskExecution（`task_executions` 表，storage 实体）

| 字段 | 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|------|
| id | `id` | Long | PK，AUTOINCREMENT（IDENTITY） | 自增 |
| taskId | `task_id` | String | NOT NULL | 关联 scheduled_tasks.task_id |
| sessionId | `session_id` | String | 可空 | 本次触发的钟推 Session（scheduler 三元组） |
| startedAt | `started_at` | Instant | NOT NULL | 开始时刻（UTC） |
| success | `success` | Boolean | NOT NULL | 成功失败都记（宪法 V 同源） |
| errorMessage | `error_message` | String | 可空 | 失败时人可读消息（非堆栈） |
| durationMs | `duration_ms` | Long | 可空 | 执行耗时（毫秒） |

## 状态机（scheduled_tasks 生命周期）

```
注册（registerAll，含 next_run_at 计算）
  → enabled=true, run_count=0, last_run_at/last_status=null
        │
        ├─ 到点触发（runOnce）：
        │    enabled=false ──→ 跳过：不执行、不记历史、不改任何状态
        │    enabled=true  ──→ executeInternal(waitForLock=false)：
        │       锁被占 ──→ 跳过（不记历史、不改状态，008 坑二）
        │       拿到锁 ──→ 执行 process：
        │         成功 ──→ recordExecution(success=true) + last_run=now、
        │                  last_status=success、run_count+1、next_run 重算
        │         失败 ──→ recordExecution(success=false, error_message 人可读非堆栈)
        │                  + last_run=now、last_status=failure、run_count+1、next_run 重算
        │
        ├─ runNow(taskId)（管理台立即执行，S5 analyze F1 修正）：
        │    无视 enabled，executeInternal(waitForLock=true)（与到点同锁同入口）：
        │       锁被占 ──→ lock() 阻塞排队（POST /run 必须返回执行记录；
        │                  60s 端点超时兜底，任务体后台跑完审计照常）
        │       拿到锁 ──→ 执行 process + 成败落账（同上）
        │    返回本次 TaskExecutionView
        │
        └─ setEnabled(false/true)：
             只改 enabled 字段；next_run_at 保留原值不动（Clarifications 2026-09-09）；
             重新启用后 next_run_at 在下次真正执行时按 cron 更新（期间显示旧值，接受）
```

## 值对象（storage，契约面——落位拍板 2026-09-09）

```java
// ScheduledTaskStore 列表/登记的对外视图（storage，与 SessionRepository 同构——JPA 实体不上浮）
record ScheduledTaskView(
    String taskId, String profileName, String cron, String zone, String message,
    boolean enabled, Instant nextRunAt, Instant lastRunAt, String lastStatus, int runCount) {}

record TaskExecutionView(
    Long id, String taskId, String sessionId, Instant startedAt,
    boolean success, String errorMessage, Long durationMs) {}
```

## 校验规则

| 规则 | 位置 | 行为 |
|------|------|------|
| frontmatter schedules 条目缺 `id` | AgentLoader 解析（003 交付物改造） | **启动报错**（不静默、不派生兜底，⑦c——兜底退回 008 派生 key 断链风险复活） |
| id 冲突（跨 Profile 全局唯一，task_id 主键语义） | AgentScheduler.registerAll | **启动报错且指明冲突的 Profile**（运营方猜不出来，⑦ P2） |
| zone 非法 | AgentScheduler（008 已有 validatedZone） | 启动报错（007 同款纪律） |
| cron 非法 | Spring CronTrigger 构造 | 启动报错（008 ⑦b 口径延续） |
| 任务不存在（四端点按 id 访问） | ScheduleApiController | 404（ResourceNotFoundException，009 单出口） |

## 与其他实体的关系

- `task_executions.session_id` → `sessions.session_id`：钟推 Session（channel/user 固定 `scheduler`，008/宪法 VIII），同一 Profile 历次触发复用同一 Session
- `scheduled_tasks.task_id` ← `Profile.schedules[].id`：定义源是 frontmatter（文件），表只存「状态 + 历史」不作为定义源——重启时从文件重新注册（技术方案 §8.5）
- 无外键约束（SQLite 核心阶段轻约束，与 sessions 现状一致）；一致性靠写入路径保证

## 存储形态

- schema.sql 手工增量两表（`CREATE TABLE IF NOT EXISTS`，幂等；既有 `spring.sql.init.mode: always` 管线自动执行，零接线改动）
- 时间列统一 `Instant` + `InstantTextConverter`（ISO-8601 TEXT，UTC）
- `ddl-auto: none` 不变；SQLite ALTER 不依赖 Hibernate（坑八）
