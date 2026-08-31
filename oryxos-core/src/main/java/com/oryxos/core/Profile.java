package com.oryxos.core;

import java.util.List;

/**
 * Agent 运行时配置——派生自 {@code .oryxos/agents/<name>/AGENT.md} 的 frontmatter （基础版派生见 {@link
 * ProfileLoader}）。
 *
 * <p>类本身一次建全：后续各节（Memory / Tool / Channel 等）用到哪个字段就取哪个字段， 各自字段的校验规则归后续各节补充。本节的校验范围只有一条：{@code
 * provider.name} 必须命中全局层 {@code oryxos.providers} 声明（见 {@link ProfileLoader#deriveProfile}）。
 */
public record Profile(
    String name,
    String description,
    Identity identity,
    ProviderRef provider,
    List<String> tools,
    List<String> mcpServers,
    List<String> channels,
    List<Schedule> schedules,
    List<String> bootstrap,
    Settings settings) {

  /** 防御性拷贝：不暴露可变集合的内部表示。 */
  public Profile {
    tools = List.copyOf(tools);
    mcpServers = List.copyOf(mcpServers);
    channels = List.copyOf(channels);
    schedules = List.copyOf(schedules);
    bootstrap = List.copyOf(bootstrap);
  }

  @Override
  public List<String> tools() {
    return List.copyOf(tools);
  }

  @Override
  public List<String> mcpServers() {
    return List.copyOf(mcpServers);
  }

  @Override
  public List<String> channels() {
    return List.copyOf(channels);
  }

  @Override
  public List<Schedule> schedules() {
    return List.copyOf(schedules);
  }

  @Override
  public List<String> bootstrap() {
    return List.copyOf(bootstrap);
  }

  /** Agent 展示名与任务指令正文。 */
  public record Identity(String agentName, String prompt) {}

  /**
   * Provider 引用（Profile 层：声明"这个 Agent 怎么用"）。
   *
   * @param name 必须命中全局层 {@code oryxos.providers} 的 Provider 名
   * @param model 模型名
   * @param temperature 温度
   */
  public record ProviderRef(String name, String model, Double temperature) {}

  /** 定时计划（AgentScheduler 到点触发，zone 缺省按系统时区）。 */
  public record Schedule(String cron, String zone, String message) {}

  /** 运行参数；未配置的项取默认值。 */
  public record Settings(Integer maxIterations, Integer maxHistoryTurns) {

    public static final int DEFAULT_MAX_ITERATIONS = 10;
    public static final int DEFAULT_MAX_HISTORY_TURNS = 20;

    @Override
    public Integer maxIterations() {
      return maxIterations == null ? Integer.valueOf(DEFAULT_MAX_ITERATIONS) : maxIterations;
    }

    @Override
    public Integer maxHistoryTurns() {
      return maxHistoryTurns == null ? Integer.valueOf(DEFAULT_MAX_HISTORY_TURNS) : maxHistoryTurns;
    }
  }
}
