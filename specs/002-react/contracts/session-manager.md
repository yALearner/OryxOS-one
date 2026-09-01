# 接口契约：SessionManager（最小契约）

> **跨节契约**：第 18 节将把它 JPA 化（sessions 表 + 跨重启恢复），契约不变、只换实现。修改本契约视为修改公共接口，必须停下报告。

## 调用入口

```java
Session getOrCreate(String channel, String userId, String profileName)
Optional<Session> get(String sessionId)
void save(Session session)
```

| 元素 | 说明 |
|------|------|
| `getOrCreate` | 同一 (channel, userId, profileName) 三元组**幂等**返回同一 Session 实例；三元组任一不同 = 不同 Session |
| `get` | 按 id 查（本节内存版） |
| `save` | 持久化占位——内存版为 no-op，第 18 节 JPA 化后落库 |

## 行为不变量

1. **session_id 拼接只发生在 SessionManager 内一处**（H4 不变量四，架构断言钉死）：`channel + user + profileName` 联合生成
2. Session 内历史以 001 的 `Message` 承载，框架无关、可 JSON 序列化（第 18 节落库直接序列化）
3. 并发安全：内存版以 `ConcurrentHashMap` 承载（虚拟线程并发复用）

## 消费方

- 当前：`AgentService.process` 末尾 `save(session)`；后续各触发源经 AgentService 间接使用
- 后续：第 18 节 CLI Channel 维护当前 Session；第 25 节钟推 Session（channel/user = scheduler）复用同一公式
