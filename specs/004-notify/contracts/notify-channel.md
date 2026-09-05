# 接口契约：NotifyChannelAdapter（出站通知通道）

> **跨节契约**：本节交付后，第 20 节（工具注册/装配接线）、第 25 节（定时触发后推送）、第 27/28 节（全流程串联）、第 31 节（Demo 二/三 日报推送）为消费方；Web Service 节做渠道 CRUD 时直接消费 `NotifyChannelRepository` 与本表口径。**后续节不得改动已验收行为**；修改本契约视为修改公共接口，必须停下报告。

## 接口形态（接口先行，签名零渠道实现词）

```java
void send(NotifyTarget target, String content)   // NotifyChannelAdapter 唯一方法

NotifyTarget = record(String channelType, Map<String, String> config)
```

| 元素 | 说明 |
|------|------|
| `send` | 表达"把一条内容送到某个通知目标"的意图；签名不出现"webhook""企业微信""飞书"等任何一档实现特有的词 |
| `NotifyTarget.channelType` | 渠道类型——adapter 显式 Map 的选键 |
| `NotifyTarget.config` | 具体是 webhook 地址还是认证信息由实现类自己解释（核心阶段键约定：`url` = 推送地址、`name` = 渠道注册名） |

## 行为不变量

1. **接口中立**：换企业微信官方 SDK 等专用实现，`send(NotifyTarget, String)` 签名不需改动（思维练习验收锚点）
2. **URL 只从 config 取**：`WebhookNotifyAdapter` 不得硬编码任何地址（FR-3）
3. **失败不吞（坑十一）**：webhook 5xx 或网络失败时异常原样上抛——吞掉 = Agent 以为发出去了；错误路径由 `ToolExecutor` 既有审计承接（`success=false`）
4. **enforce 先于 send（坑十）**：`NotifyTools.execute` 内 `sandbox.enforce(new SandboxAction(HTTP_REQUEST, url))` 必须在 `adapter.send` 之前执行——顺序反了就是白名单被"往外推"绕过（002 contracts/sandbox.md 行为不变量三的接线约定）
5. **adapter 显式映射**：`NotifyTools` 按 `target.channelType()` 从装配处显式 `Map<channelType, NotifyChannelAdapter>` 选择，不靠容器扫描；未知 type → 明确报错。加新渠道 = 新增实现类 + 映射表加一行，已验收代码零改动
6. **成功返回带渠道名**：`ToolResult.success("已推送到 " + name)`——审计 `result_json` 可查出推给了谁
7. **缺省口径（拍板）**：`channel` 缺省仅在注册表恰好一条渠道时取它；多条或为空 → 明确报错（推错群是不可见的错误）
8. **注册表纯数据**：`NotifyChannelRegistry` 只做按名解析，adapter 选择不在本类（拍板：跨节契约越小越稳）
9. **装配不变量（第 20 节装配处 MUST 遵守，S2 复盘补钉 2026-09-05）**：`RestClient` MUST 用 Boot 自动配置的 `RestClient.Builder` 构建并设 connect/read timeout（建议 connect 3s / read 10s）——MUST NOT 用裸 `RestClient.builder()`（无连接池、无 timeout，慢 webhook 会把整个 ReAct 轮次挂死）；adapter 显式映射 `Map.of("webhook", webhookAdapter)` 由装配处构建后注入 `NotifyTools`

## 核心阶段唯一实现

- `WebhookNotifyAdapter`：对 `target.config["url"]` 发 POST，content-type JSON、body 为通用 text 格式 `{"msgtype":"text","text":{"content": ...}}`（企业微信与钉钉共用；2026-09-05 人工验收 40008 实锤修正——裸 `{"content":...}` 是 invalid message type）；`RestClient` 构造注入（装配处 `RestClient.Builder` 构建并设 connect/read timeout）
- 只认 HTTP 状态码：body errcode/code 解析、签名算法、AccessToken 刷新、富文本卡片均明确不做（扩展阶段按渠道专用 Adapter）；飞书 text 格式不同（msg_type/content/text），归扩展阶段专用 Adapter

## 演进

- 核心阶段：`Map.of("webhook", webhookAdapter)`（第 20 节装配处落；RestClient 经 Boot 自动配置 builder + timeout，见不变量 9）
- 扩展阶段：企业微信/飞书/钉钉专用 Adapter 只新增实现类 + 映射表加一行；接口、`NotifyTools`、注册表口径均不变
