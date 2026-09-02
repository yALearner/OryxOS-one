# Flow Status: 003-cli

需求文档: docs/requirements/003-cli.md（课件第 18 节：CLI + Session 持久化）
feature.json 指针: specs/003-cli/（切换前: specs/002-react）
分支: 003-cli（自 main 创建，2026-09-02；001/002 均已合并 main）
创建时间: 2026-09-02

| 阶段 | 状态 | 产物 | 哈希 | 门禁结论 | 备注 |
|------|------|------|------|---------|------|
| S1 specify | done | spec.md | 2ce1102c | —（质量检查全过，无 NEEDS CLARIFICATION） | 5 个 US；FR-001~014；checklists/requirements.md 16 项全过 |
| S2 clarify | done | spec.md（术语对齐） | abf21f97 | G1: 通过（0 个澄清问题，无未决项） | 10 类覆盖扫描全 Clear；术语漂移 SessionArchive→SessionEntity 机器自修 |
| S3 plan    | done | plan.md + research/data-model/contracts/quickstart | — | G2: 六条全通过（用户确认） | 无跨模块摩擦点；改造点（SessionManager 换实现）已列需求文档 |
| S4 tasks   | done | tasks.md（T001~T029） | — | G3: 通过 + 固定软停点：交付清单比对齐、多 1 测试类名（用户确认补列需求文档） | 29 任务 8 阶段；US1=MVP；审计延续由坑九断言显式化 |
| S5 analyze | done | 分析报告（对话输出） | — | G4: 通过（0 ERROR / 0 CRITICAL / 0 MEDIUM） | 2 LOW：C1 纪律项放行、F1 plan 测试类措辞已修（用户确认） |
| S6 implement | done | 代码（29 任务全完成） | — | 测试: mvn clean verify 全绿（55 tests + 全部静态门禁） | 收尾 DoD 七项全过；轻命令实跑验证通过；2026-09-02 |

WARNING 记录: （累计 0/3）

停止清单触发记录:
- （2026-09-02 S0）分支检查：当前在 main → 按项目既有 {NNN}-{slug} 约定自 main 新建 003-cli（001/002 均已合并，无 PR 依赖问题）
- （2026-09-02 S6）停止清单第 1 条：Picocli 分组迫使新增 4 个父命令类（ProfileCommand/SessionCommand/ProviderCommand/ToolCommand，机器可判结构件）→ 已补列需求文档交付清单（用户未提出异议）
- （2026-09-02 S6 实跑发现）三个打包/接线缺口修复：① cli repackage 嵌套 fat jar 类加载失败 → cli 插件 skip、boot 唯一打包入口（mainClass 迁 boot pom）② chat 启动拉起 Tomcat 抢 8080 → 重命令显式 web(NONE) ③ 建表脚本无人执行（001 review 留白到期）→ application.yaml spring.sql.init.mode=always + classpath:schema.sql

人工验收待办（机器已判卷之外的部分）:
- [x] init 幂等、profile 四命令、status/provider list/tool list/session list 轻命令实跑（2026-09-02，boot fat jar）
- [x] 坑九实机核验：chat 启动日志 Found 3 JPA repository interfaces（llm_calls/tool_invocations/sessions）
- [x] 12 命令 --help 全可用（9 命令组 12 叶命令，boot jar 实测）
- [x] provider list 输出不含 api-key（占位未解析）；profile show 同（${DEEPSEEK_API_KEY} 原样展示）
- [x] chat 无 key/坏 key 报错路径清晰（401 从模型侧透传，异常不吞）
- [x] chat 多轮对话 + /quit + 跨重启恢复（2026-09-02 23:35~23:37 用户实跑通过）：中文正常（终端编码修复：Scanner 跟随 console charset）；会话内"我们刚才聊了什么"完整复述；**跨进程恢复铁证**——新进程复述了上一进程（23:22）的乱码消息与 /quit 历史（messages_json 序列化回读）；session list 两条会话、last_active 时间戳正确
- [x] serve/gateway 占位启动不抢 8080（web(NONE) 修复后机器实测；占位文案属文本项）
