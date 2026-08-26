# 日志约定与坑位（步骤 3 配套）

## 分层约定

- **全部日志走 SLF4J**（`LoggerFactory.getLogger`），禁止 `System.out`/`System.err` 打日志。
  例外：CLI 用户交互输出（如 picocli usage/chat 回复）是程序输出而非日志，可走 stdout，
  但实现类内部状态一律走日志。
- **会话级上下文用 MDC**：`session_id`（会话标识，业务写入）、`traceId`（请求链路，Web 场景由框架/Filter 写入）。
- **事件级字段用 `StructuredArguments.kv()`**（logstash-encoder 提供）：
  ```java
  log.info("tool call finished", StructuredArguments.kv("tool", toolName),
      StructuredArguments.kv("duration_ms", duration));
  ```
  不要拼字符串进 message——JSON 输出里结构化字段可被日志平台直接索引。

## 版本兼容矩阵

| LogstashEncoder | Logback | Jackson | 可用性 |
|----------------|---------|---------|--------|
| 8.x | 1.5.x（Boot 3.4/3.5 自带） | 2.x | ✅ 当前选型 |
| 9.x | 1.5.x | **3.x** | ❌ Boot 3.5 用 Jackson 2，混用直接报错 |

选型规则：**Encoder 版本跟随 Boot 自带 Logback 线，先查 `spring-boot-dependencies` 的
logback.version，再选对应 Encoder 大版本**（8.x↔1.5.x；9.x↔Jackson 3/Boot 4）。

## 配置骨架（templates/logback-spring.xml.tpl）

- 默认/dev/CLI profile：彩色 console pattern
- prod profile：console 与文件均 JSON（LogstashEncoder，includeMdcKeyName=traceId/session_id）
- JSON 滚动文件 `logs/<app>.jsonl` 常开（SizeAndTimeBasedRollingPolicy + gz 压缩），供审计采集
- 日志目录可用 `${LOG_DIR:-logs}` 环境变量覆盖

## 坑位

1. **虚拟线程下 MDC 不跨线程继承**：Java 21 virtual thread 由调度器承载，`MDC` 上下文
   不会自动传到 `newVirtualThreadPerTaskExecutor` 派生的任务里。需要手动在任务边界
   `MDC.put`/`MDC.remove`（用 try/finally），或统一用 `StructuredArguments` 事件级字段替代。
2. **`includeMdcKeyName` 必须在 LogstashEncoder 启动前配置**（XML 里写属性即可），
   运行时 MDC 键变化不会自动纳入。
3. **日志文件目录要进 .gitignore**（`logs/`），避免审计日志误入库。
