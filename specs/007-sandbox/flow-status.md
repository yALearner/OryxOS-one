# Flow Status: 007-sandbox

需求文档: docs/requirements/007-sandbox.md（课件第 23/24 节：Sandbox 三层白名单；整体以 D:\项目\ 新版 PDF 为准，PyMuPDF 提取已解决）
feature.json 指针: specs/007-sandbox/（切换前: specs/006-memory）
分支: 007-sandbox（自 docs-flow-occlusion-fix 当前 HEAD 创建，2026-09-06；用户拍板分支策略「从当前分支建」）
创建时间: 2026-09-06

| 阶段 | 状态 | 产物 | 哈希 | 门禁结论 | 备注 |
|------|------|------|------|---------|------|
| S1 specify | done | spec.md | 372cf62fe5a71442b447cc1c07c2a81ebf96650860cfea1e0fb0eeef247e9aed | — | 2026-09-06 首跑生成；无 NEEDS CLARIFICATION（修订说明 ①~⑦ 口径全钉死） |
| S2 clarify | done | spec.md（更新） | 372cf62fe5a71442b447cc1c07c2a81ebf96650860cfea1e0fb0eeef247e9aed | G1: pass | 0 题澄清——修订说明 ①~⑦ 口径全钉死；grep 无 open question/TODO/待定 |
| S3 plan    | done | plan.md | 7aa5b31f0990449957e926f1966568e9296f3cc2c27e3cfac5d5f57b51ae7e5c | G2: pass（六条全部用户确认通过，2026-09-06） | research/data-model/quickstart/contracts 同批产物 |
| S4 tasks   | done | tasks.md | d51b8561523445404eff1c79a587db574c2a3ef888b863e431bc980757698f1d | G3: pass（机器复核）+ 软停点用户确认 | 17 任务；比对结果：交付清单 10 类全齐、缺 0——用户确认（2026-09-06）；多出 T001/T016/T017 为流程承载任务 |
| S5 analyze | done | 分析报告（对话输出） | — | G4: 通过（0 ERROR / 0 CRITICAL） | 2 MEDIUM + 1 LOW（C1/C2/C3）经用户确认全部修复后复核清零；覆盖 15/15（100%）；tasks.md 修复后哈希 2d9dd0a4ec42c90f9b38298c40a48232766fea839f746d7be0ed6fbc11ed8d01 |
| S6 implement | done | 代码（17 任务全完成） | — | 测试: mvn clean verify 全绿（2026-09-07，含 Spotless/SpotBugs/ErrorProne 门禁） | 收尾 DoD 七项全过；SpotBugs 3 项 EI_EXPOSE_REP 经 004/006 先例抑制（record 访问器为构造器绑定契约要求，构造期 copyOf 后不保留引用）；ErrorProne StringSplitter 用 split(regex, 2) 满足（首 token 语义不变）；PermissiveSandbox 删除（编译暴露引用点仅 CliAgentConfiguration 一处，已同步替换） |

WARNING 记录: （累计 0/3）

停止清单触发记录:
- （2026-09-06）S0 分支检查（hook 未配置）→ 用户拍板「从当前分支建 007-sandbox」

人工验收待办（机器已判卷之外的部分）:
- ① 集成验证（真实装配链路）：✅ 2026-09-07 闭环——临时 harness SandboxManualIT（已删除）四段证据：success=false + errorMessage=命令不在白名单内: rm + tool_invocations 真实落账 1 行 + 断言全绿
- ② 接口中立性自查（用户思维练习）：✅ 2026-09-07 闭环——用户三问全对（microVM 套 enforce(SandboxAction) 不需加方法 / 签名无「白名单/容器/镜像」字样 / target 对 microVM 有意义）
- ③ 配置边界核对：✅ 四条口径注释齐全（fail-closed / 相对根按启动目录解析 / 不可热更新 / 劝阻级诚实标注）
- ④ 上层零改动目检：✅ git diff 空输出（七工具主代码、接口墙四件、ToolExecutor 逐字节未动）
- 偏差排查（2026-09-07）：代码 vs 需求文档无实质偏差；两处字面级差异（split(regex,2) 满足 ErrorProne 门禁、record null→空归一满足构造器绑定 fail-closed）为门禁/绑定语义要求的实现级补充，记录于 docs/reviews/007-sandbox-review.md §三 4/5 条
