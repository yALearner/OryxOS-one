# Data Model: 007-sandbox

> 本节无新数据表——审计复用 002 已交付的 `tool_invocations`（违规落 `success=false` + `error_message`）。数据形态 = 三块白名单配置契约 + WhitelistSandbox 构造期不可变状态。`SandboxAction`/`SandboxViolationException` 为 002 已交付值对象，零改动。

## 三块白名单配置（application.yaml，本节新增契约）

| 配置键 | 语义 | 空值口径 |
|--------|------|---------|
| `file.allowed_paths` | 文件读写允许根目录列表（List<String>）；相对根按启动目录解析（构造期 toAbsolutePath，⑦a） | 空 = 全部拒绝（fail-closed） |
| `shell.allowed_commands` | Shell 首 token 允许命令列表（List<String>）；精确匹配、大小写敏感 | 空 = 全部拒绝 |
| `http.allowed_domains` | HTTP 允许域名列表（List<String>）；精确全等或 `*.` 前缀通配（带点号边界） | 空 = 全部拒绝 |

- 任一为空 → 构造期 WARN（⑦c）；配置键名非用户可控值，不违反 NFR-3 日志口径
- 不可热更新：构造期拷贝（`Set.copyOf`/`List.copyOf`），改配置须重启（⑦i）

## WhitelistSandbox 内部状态（构造期一次性，全部不可变）

| 字段 | 类型 | 构造 | 用途 |
|------|------|------|------|
| `allowedRoots` | `List<Path>` | `allowedPaths().stream().map(Path::of).map(Path::normalize).map(Path::toAbsolutePath).toList()` | checkFilePath 前缀匹配；Windows 下 lower-case 归一比较（⑦b） |
| `allowedCommands` | `Set<String>` | `Set.copyOf(allowedCommands())` | checkShellCommand 首 token 精确匹配 |
| `allowedDomainPatterns` | `List<String>` | `List.copyOf(allowedDomains())` | checkHttpUrl matchesDomain（精确 / `*.` 通配） |

- 不可变 + 无共享可变状态 → 虚拟线程并发下线程安全，无需同步
- 三校验方法 private：外部只看得到 `enforce(SandboxAction)` 一个入口（接口不带偏，课件 24 §3.2）

## 既有值对象（002 已交付，本节零改动）

| 值对象 | 形态 | 本节关系 |
|--------|------|---------|
| `SandboxAction` | record（`ActionType type, String target`）——纯数据，无"白名单/容器/镜像"字样 | 调用方传入，不改 |
| `ActionType` | 枚举四值 `FILE_READ / FILE_WRITE / SHELL_COMMAND / HTTP_REQUEST` | enforce switch 路由；FILE_READ/FILE_WRITE 同路由到 checkFilePath |
| `SandboxViolationException` | 普通 RuntimeException，message = 人类可读违规原因（如"命令不在白名单内: rm"） | 校验失败抛出 → ToolExecutor 既有 catch 接住（FR-007） |

## 审计落点（既有表，不改结构）

- 违规 → `tool_invocations`：`success=false`、`error_message` = 违规原因、`retryable` 维持 002 现状（true）
- 放行 → 正常执行路径照旧审计（成功/失败都落库，宪法 V）
