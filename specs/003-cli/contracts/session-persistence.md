# 接口契约：Session 持久化（SessionEntity / SessionRepository / SessionManager JPA 版）

> **跨节契约**：第 26 节（Web 的 GET /sessions/{id}、DELETE 归档）按本表口径读写；第 25 节钟推 session 复用同一 id 公式。SessionManager 的对外签名（`getOrCreate`/`get`/`save`）是 002 已验收契约，本课只换实现——后续修改仍视为修改前序公共接口，必须停下报告。

## 会话存档行（`sessions` 表，schema.sql 手工增量）

| 列 | 说明 |
|------|------|
| `session_id` TEXT PK | 标识 = channel\|user\|profileName，拼接只此一处 |
| `profile_name` / `channel` / `user_id` | 三元组原值 |
| `messages_json` TEXT | 对话历史整体 JSON 序列化（含 toolCall/toolResult 嵌套），一列存不拆表 |
| `status` TEXT | `active` / `archived`（本节只写 active） |
| `created_at` / `last_active_at` / `archived_at` TEXT ISO-8601 | 时间戳（复用 InstantTextConverter；archived_at 可空） |

## SessionManager（JPA 版）行为不变量

1. **契约不变**（002 已验收）：`getOrCreate(channel, userId, profileName)` / `get(sessionId)` / `save(session)` 签名与语义不变
2. **幂等**：同三元组两次 getOrCreate 返回**同一实例**（进程内缓存保证）+ 同 id（落库唯一）
3. **跨重启恢复**：进程重启后 getOrCreate 命中库中行 → messages_json 反序列化重建历史
4. **id 拼接只此一处**（H4 不变量四，架构断言钉死）；`Session` 无公开构造器（002 钉死）
5. **save 语义**：messages 序列化落库 + `last_active_at` 刷新；序列化失败报错不静默
6. **转换收口**：Session（core 内存容器）↔ SessionEntity（storage 行）的转换只发生在 SessionManager 私有方法内，其他类不得直接读写 messages_json

## 消费方

- 当前：`CliChannel`（chat 交互）、`SessionListCommand`（JDBC 直读概要）
- 后续：第 25 节 AgentScheduler（钟推 session：channel/user 固定 scheduler）、第 26 节 Web Service（SessionRepository + 归档流转）
