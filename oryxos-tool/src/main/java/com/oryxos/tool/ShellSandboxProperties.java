package com.oryxos.tool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shell 命令白名单配置（{@code shell.allowed_commands}，007-sandbox FR-2）。
 *
 * <p>首 token 精确匹配、大小写敏感（007-sandbox FR-4）；纯数据 record——按 G4-C1 无组件注解，由装配处经
 * {@code @EnableConfigurationProperties} 注册后注入 {@link WhitelistSandbox}。空 = 什么都不允许（fail-closed）。
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification =
        "record 访问器为 Spring Boot 构造器绑定契约要求；实际暴露风险为零——WhitelistSandbox 构造期"
            + " Set.copyOf 拷贝后不再保留本对象引用（004/006 EI 抑制同款先例）")
@ConfigurationProperties(prefix = "shell")
public record ShellSandboxProperties(List<String> allowedCommands) {

  public ShellSandboxProperties {
    // 构造器绑定缺失配置键时给 null——归一为空列表：空 = 什么都不允许（fail-closed），不得 NPE
    if (allowedCommands == null) {
      allowedCommands = List.of();
    }
  }
}
