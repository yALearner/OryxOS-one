# 人工验收方法论（机器判卷之外的验收）

> 适用时机：S6 implement 完成、收尾 DoD 第 6 条「剩余人工项清单」确认后。
> 首发于 002-react（2026-09-02，Demo 一真模型跑通）；后续各节复用本方法，节级细节写进各节 quickstart.md。

## 原则

1. **能自动化的不留给人工**：凡可 mock 的行为（循环、截断、审计、异常路径）全部由 harness 钉死；
   人工只验"测不出来"的：真模型行为、真实网络、真实落库、代码阅读判断。
2. **真实验证 ≠ 生产代码**：用「临时 harness」跑真装配，**不进交付物**，验收后删除。
3. **如实记录**：验证结果（含失败重跑）写进 flow-status「人工验收待办」；缺 key / 缺工具导致的
   无法验证项如实记为已知缺口，不伪装通过。

## 临时 harness 模板（真装配 + 真落库 + 真模型）

放在 `oryxos-boot/src/test/java/com/oryxos/boot/`（**未提交、验收后删除**），八个要点：

1. `@Tag("integration")`——surefire 默认排除，CI 不受外网影响；跑法 `-Dtest.groups=integration -Dtest.excludedGroups=`
2. `@SpringBootTest(classes = OryxOsApplication.class)` 拿到真实 Spring 上下文 + 真实 JPA Repository；
   **仓储/实体扫描**：主类已声明 `@EnableJpaRepositories/@EntityScan(basePackages="com.oryxos.storage")`（002 fix），无需在测试重复
3. **占位属性覆盖**必须补全整元素字段（只覆盖一个字段会把 YAML 列表项的其余字段顶掉触发启动校验）：
   ```java
   properties = {
     "oryxos.providers[0].name=demo", "oryxos.providers[0].api-key=demo-dummy",
     "oryxos.providers[0].base-url=http://127.0.0.1:9", "oryxos.providers[0].model=demo-model",
     // [1] 同款
   }
   ```
4. **真 key 在测试内手工装配**（001 冒烟同款：`OpenAiApi.builder().apiKey(System.getenv("DEEPSEEK_API_KEY"))...`），
   测试开头 `Assumptions.assumeTrue(key != null && !key.isBlank())`——无 key 时 SKIP 不是 FAIL
5. **手工 schema.sql 落库**（坑八）：`ClassPathResource("schema.sql")` 读脚本、按 `;` 切分逐条 execute——
   测试与生产同一份脚本；`.oryxos` 父目录在 static 块先建（数据源相对路径，连接池启动即连）
6. **真实链路装配**：真实 ProviderService（真 Repository）→ 真实 SessionManager/ProfileRegistry/ContextLoader/
   PromptBuilder/ToolExecutor/ReActLoop/AgentService；临时工具（如 JDK HttpClient 版 http_get）打底
7. **打印完整对话链**（角色/内容/工具请求/工具结果）——人工核对"想→做→看"的实拍证据
8. **审计断言**：`llmCallRepository.count() ≥ 2`（多轮）、`toolInvocationRepository.count() ≥ 1`、
   逐行打印两表记录（session 关联、success、durationMs）

## 标准验证步骤（6 步）

1. **环境**：`export JAVA_HOME/PATH`（本机未配置）；PowerShell 用 `$env:` 且 mvn 参数**一行写完**
   （空值参数必须带引号 `"-Dtest.excludedGroups="`，折行粘贴会解析错误）
2. **跑 harness**：`DEEPSEEK_API_KEY=xxx mvn -pl oryxos-boot -am test -Dtest=XXXManualIT -Dtest.groups=integration -Dtest.excludedGroups= -Dsurefire.failIfNoSpecifiedTests=false`
3. **核对四段证据**：① 最终答复非空可读 ② 对话链含工具请求+结果 ③ 审计核对行数量达标 ④ 断言全绿
4. **落库核对**：审计行 session 关联正确、success=true、durationMs 有值；
   注意 surefire 工作目录 = 模块目录，相对路径数据源落 `<module>/.oryxos/oryxos.db`（生产 java -jar 从仓库根启动落根 `.oryxos/`，行为一致）
5. **code review 证据**：grep 无禁用路径（如 `ChatClient`/`ToolCallingManager`/`executeToolCalls`）、
   核心类行数符合"自实现数十行"预期
6. **清理 + 记录**：删除临时 harness 与演示数据（用户拍板）；flow-status「人工验收待办」逐项勾选，
   无法验证项如实记为待办（如"第 20 节工具就位后补跑"）

## 本机已知坑（踩过一遍，别再踩）

| 坑 | 现象 | 解法 |
|----|------|------|
| PowerShell 折行粘贴 | `>>` 续行接两个引号参数 → ParserError | mvn 参数一行写完 |
| surefire 工作目录 = 模块目录 | 相对路径数据源/文件落 `<module>/.oryxos/` 而非根 | 预期行为；核对时指对路径即可 |
| `scanBasePackages` 不作用于 JPA 扫描 | 真实启动时 Repository/实体 Bean 缺失 | 主类显式 `@EnableJpaRepositories/@EntityScan`（002 fix 已落） |
| 索引式属性覆盖 | 只覆盖 `providers[0].api-key` 会把 name 顶掉 → 启动校验失败 | 覆盖整元素全部字段 |
| 本机无符号链接特权 | `Files.createSymbolicLink` 抛"客户端没有所需的特权" | 测试用 `mklink /J` junction（Java 以 `isOther()` 识别）；ContextLoader.isBinding 已双形态支持 |
| Mockito 混合 matcher 与裸值 | `InvalidUseOfMatchers` | 全用 matcher（裸值包 `eq(...)`） |
| Error Prone `-Werror` | `LocalDateTime.now()`/`split(regex)`/`toLowerCase()` 等触发告警即失败 | 显式时区 / `split(regex, -1)` / `toLowerCase(Locale.ROOT)` / 显式 UTF_8 |
| FindSecBugs CRLF 注入 | 用户可控值进日志参数被拦 | 日志参数不带 sessionId/名称类字段（001 先例），关联信息在审计表 |

## 与流程的关系

- 收尾 DoD 第 6 条（验收报告）→ 本文件
- 节级「人工部分」写在需求文档「验收标准」，节级跑法细节写 quickstart.md，本文件是通用模板
- 结果回写 flow-status「人工验收待办」+ PR 正文「人工验收」段
