# Data Model: 008-scheduler

> 本节无新数据表（scheduled_tasks/task_executions 归 28 节）。数据形态 = `Profile.Schedule`（002 已交付定义源）+ 派生锁 key（本节约定）+ AgentScheduler 内部两张内存 Map。

## Profile.Schedule（002 已交付，本节零改动）

| 字段 | 类型 | 语义 | 本节关系 |
|------|------|------|---------|
| `cron` | String | cron 表达式（**Spring 6 只接受 6 段含秒**——⑧b H3 实测；CLAUDE.md 示例 `0 8 * * *` 为五段示意，实际须 `0 0 8 * * *`） | 注册参数 |
| `zone` | String（可空） | 时区 ID；空 = 系统时区（javadoc 口径）；**非空须 ZoneId 合法——非法启动报错（⑦b）** | 注册参数 + 校验点 |
| `message` | String | 到点发给 Agent 的消息内容 | runOnce → AgentService.process |

- 定义源 = AGENT.md frontmatter `schedules` 列表（AgentLoader 解析，003 已交付）；改 cron/message 需重启生效（核心阶段边界，28 节前）

## 派生锁 key（本节约定，拍板 B）

- 规则：`profileName + "|" + cron + "|" + message`
- 用途：`taskLocks`（per-task ReentrantLock）与 `scheduledTasks`（ScheduledFuture 句柄）两张 Map 的共同 key；日志标识
- 稳定性口径：进程内稳定（Schedule 对象在注册闭包内不变）；**28 节 task_id 不得照搬**（⑦d：message 是自然语言，改一字重启即 key 漂移、执行历史断链）——28 节必须重议（候选：给 Schedule 补 id / 换 profileName+cron+zone 稳定派生）

## AgentScheduler 内部状态（构造期/注册期，全部并发安全）

| 字段 | 类型 | 语义 |
|------|------|------|
| `taskLocks` | `ConcurrentMap<String, Lock>` | per-task ReentrantLock（坑二防重叠；数量 = 注册任务数，有界无泄漏） |
| `scheduledTasks` | `ConcurrentMap<String, ScheduledFuture<?>>` | 注册句柄（⑦c：28 节启停/重调度零重构） |
| 注入依赖 | taskScheduler / profileRegistry / sessionManager / agentService | 四件前序交付物（实测就位） |

## 会话身份（宪法 VIII 约定）

- `sessionManager.getOrCreate("scheduler", "scheduler", profileName)`——channel 与 user 固定 `scheduler`
- 同一 Profile 历次定时触发复用同一 Session（session_id 联合生成公式不变，见 002）；对话历史靠 max_history_turns 截断兜底——不为钟推新设任何概念

## 审计落点（既有表，不改结构）

- 钟推调用走 `AgentService.process` 内部既有审计：`llm_calls`/`tool_invocations` 成败都落（宪法 V）；失败的那次调用与人推失败同路径——零新增审计逻辑（FR-003）
