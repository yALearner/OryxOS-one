package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ProfileLoader 验收 harness——frontmatter 派生、provider 引用校验、坏文件不阻断。 */
class ProfileLoaderTest {

  private static final String FULL_FRONTMATTER =
      """
      ---
      name: ops-agent
      description: 运维助手
      identity:
        agent_name: 运维小欧
        prompt: 你是一个专业的运维助手
      provider:
        name: deepseek
        model: deepseek-chat
        temperature: 0.7
      tools:
        - read_file
        - shell
      mcp_servers:
        - github-mcp
      channels:
        - name: cli
      bootstrap:
        - AGENTS.md
      schedules:
        - cron: "0 8 * * *"
          zone: Asia/Shanghai
          message: 生成今日天气和穿搭建议
      settings:
        max_iterations: 8
        max_history_turns: 15
      ---
      你是一个专业的运维助手。被触发时……
      """;

  @Test
  @DisplayName("合法 frontmatter：全字段解析")
  void parseFullFrontmatter(@TempDir Path tmp) throws Exception {
    Path agentDir = Files.createDirectories(tmp.resolve("ops-agent"));
    Files.writeString(agentDir.resolve("AGENT.md"), FULL_FRONTMATTER);

    Profile profile = new ProfileLoader().deriveProfile(agentDir, Set.of("deepseek", "kimi"));

    assertThat(profile.name()).isEqualTo("ops-agent");
    assertThat(profile.description()).isEqualTo("运维助手");
    assertThat(profile.identity().agentName()).isEqualTo("运维小欧");
    assertThat(profile.identity().prompt()).contains("专业的运维助手");
    assertThat(profile.provider().name()).isEqualTo("deepseek");
    assertThat(profile.provider().model()).isEqualTo("deepseek-chat");
    assertThat(profile.provider().temperature()).isEqualTo(0.7);
    assertThat(profile.tools()).containsExactly("read_file", "shell");
    assertThat(profile.mcpServers()).containsExactly("github-mcp");
    assertThat(profile.channels()).containsExactly("cli");
    assertThat(profile.bootstrap()).containsExactly("AGENTS.md");
    assertThat(profile.schedules()).hasSize(1);
    assertThat(profile.schedules().get(0).cron()).isEqualTo("0 8 * * *");
    assertThat(profile.schedules().get(0).zone()).isEqualTo("Asia/Shanghai");
    assertThat(profile.settings().maxIterations()).isEqualTo(8);
    assertThat(profile.settings().maxHistoryTurns()).isEqualTo(15);
  }

  @Test
  @DisplayName("引用不存在的 provider：报错清晰（含 provider 名）")
  void missingProviderFailsWithClearError(@TempDir Path tmp) throws Exception {
    Path agentDir = Files.createDirectories(tmp.resolve("bad-agent"));
    Files.writeString(
        agentDir.resolve("AGENT.md"),
        """
        ---
        name: bad-agent
        provider:
          name: nonexistent
        ---
        正文
        """);

    assertThatThrownBy(() -> new ProfileLoader().deriveProfile(agentDir, Set.of("deepseek")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  @DisplayName("坏文件不阻断其余加载：只注册合法 Agent")
  void badFileDoesNotBlockOthers(@TempDir Path tmp) throws Exception {
    Path good = Files.createDirectories(tmp.resolve("good-agent"));
    Files.writeString(
        good.resolve("AGENT.md"), "---\nname: good-agent\nprovider:\n  name: deepseek\n---\n正文\n");
    Path bad = Files.createDirectories(tmp.resolve("bad-agent"));
    Files.writeString(
        bad.resolve("AGENT.md"), "---\nname: bad-agent\nprovider:\n  name: nope\n---\n正文\n");

    Map<String, Profile> loaded = new ProfileLoader().loadAll(tmp, Set.of("deepseek"));

    assertThat(loaded).containsOnlyKeys("good-agent");
  }

  @Test
  @DisplayName("缺 AGENT.md 的目录：跳过不阻断")
  void missingAgentMdIsSkipped(@TempDir Path tmp) throws Exception {
    Path good = Files.createDirectories(tmp.resolve("good-agent"));
    Files.writeString(
        good.resolve("AGENT.md"), "---\nname: good-agent\nprovider:\n  name: deepseek\n---\n正文\n");
    Files.createDirectories(tmp.resolve("empty-dir"));

    Map<String, Profile> loaded = new ProfileLoader().loadAll(tmp, Set.of("deepseek"));

    assertThat(loaded).containsOnlyKeys("good-agent");
  }

  @Test
  @DisplayName("settings 缺省：取默认值 10/20")
  void settingsDefaultsApply(@TempDir Path tmp) throws Exception {
    Path agentDir = Files.createDirectories(tmp.resolve("defaults"));
    Files.writeString(
        agentDir.resolve("AGENT.md"),
        "---\nname: defaults\nprovider:\n  name: deepseek\n---\n正文\n");

    Profile profile = new ProfileLoader().deriveProfile(agentDir, Set.of("deepseek"));

    assertThat(profile.settings().maxIterations()).isEqualTo(10);
    assertThat(profile.settings().maxHistoryTurns()).isEqualTo(20);
  }
}
