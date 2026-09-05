package com.oryxos.tool;

/**
 * 临时全放行 Sandbox（005-tool 拍板方案 A）——**第 24 节替换为 {@code WhitelistSandbox}（002 FR-7）**， 仅用于 20~23
 * 节生产接线；替换时调用方零改动（接口不变，只换装配处一行 @Bean），**替换后本类删除**。
 *
 * <p>安全窗口口径（20~23 节）：白名单未生效期间靠内网假设 + {@code tool_invocations} 审计留痕兜底， 并执行保守 Profile 纪律——不建议 {@code
 * shell}/{@code http_post} 进任何 Agent 的 tools 声明 （需求文档 FR-7 安全窗口纪律，24 节替换后解除）。
 */
public class PermissiveSandbox implements Sandbox {

  @Override
  public void enforce(SandboxAction action) {
    // 全放行——24 节由 WhitelistSandbox 三层白名单接管
  }
}
