# 接口契约：ReAct 循环引擎

> 本契约是**跨节契约**：第 18 节（CLI + Session 落库）、第 25 节（AgentScheduler）、第 26 节（Web Service）将按此调用。修改本契约视为"修改本节交付的公共接口"，必须停下报告（执行纪律停止清单第 4 条）。

## 调用入口

```java
// AgentService（三种触发源共用的统一编排入口）
String process(Session session, String userMessage)

// ReActLoop（引擎本体；Profile 由 AgentService 放入 ProfileContext）
String run(Session session, String userMessage, Profile profile)
```

| 元素 | 说明 |
|------|------|
| `session` | 会话（含累积历史；`session.profileName()` 决定当前 Agent） |
| `userMessage` | 本次用户输入 |
| 返回 | Agent 最终答复文本；达最大轮数强制结束时为**"达到最大轮数，已停止"** |

## 行为不变量

1. **循环七步**：追加用户消息 → 组装 Prompt → 调模型（携带 `session.id()`，llm_calls 按会话关联）→ 无工具调用则返回最终答复 → 有则逐个交 `ToolExecutor.execute(session.id(), call)` → 结果回填 Session → 继续循环
2. **停止条件**：模型某轮不请求工具 = 最终答复；转满 `max_iterations`（默认 10，Profile 覆盖）= 强制停止并返回含"达到最大轮数"的文案
3. **累积**：每轮 LLM 响应与工具结果按序累积进 Session（坑三），事后可审计、下一轮接得上
4. **执行权唯一**：工具执行只发生在 `ToolExecutor` 一处；绝不触发 Spring AI 自动工具执行（坑六，回归钉死）
5. **编排**：process 依次 = 设置 ProfileContext → run → `sessionManager.save(session)` → **finally 清理 ProfileContext**（异常也清，坑四）
6. **同步**：全程同步阻塞，无异步模型（宪法 VII）
7. **LLM 调用**：一律经 001 的 ProviderService 契约（`chat(sessionId, profile, prompt)`，成败落 llm_calls、异常上抛不吞）；Provider 层只翻译不执行

## 消费方

- 当前：无（编排者本体本节交付，触发源接入从第 18 节起）
- 后续：CLI Channel（§8.4）、AgentScheduler（§8.5，channel/user 固定 scheduler）、Web Service（§7）、Notify 链路
