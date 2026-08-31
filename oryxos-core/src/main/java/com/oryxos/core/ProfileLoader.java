package com.oryxos.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Profile 派生与加载（基础版）。
 *
 * <p>扫 {@code .oryxos/agents/} 下各子目录，把每个 {@code AGENT.md} 的 frontmatter 派生成 {@link
 * Profile}。本节的校验范围只有一条：{@code provider.name} 必须命中 全局层 {@code oryxos.providers} 声明；其余字段的校验规则归后续各节补充。
 * 校验失败的 Agent 不阻断启动（记错误日志、跳过注册）。
 */
public final class ProfileLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ProfileLoader.class);

  private static final String AGENT_FILE = "AGENT.md";

  /**
   * 从单个 Agent 目录派生 Profile。校验失败抛 {@link IllegalArgumentException}（信息清晰）。
   *
   * @param agentDir Agent 目录（含 AGENT.md）
   * @param providerNames 全局层声明的 Provider 名集合
   */
  public Profile deriveProfile(Path agentDir, Set<String> providerNames) {
    Path agentFile = agentDir.resolve(AGENT_FILE);
    if (!Files.isRegularFile(agentFile)) {
      throw new IllegalArgumentException("Agent 目录缺少 " + AGENT_FILE + ": " + agentDir);
    }
    Map<String, Object> fm = parseFrontmatter(agentFile);

    String name =
        stringValue(fm, "name")
            .orElseThrow(
                () ->
                    new IllegalArgumentException("AGENT.md frontmatter 缺少必填项 name: " + agentFile));
    String description = stringValue(fm, "description").orElse(null);

    Map<?, ?> identity = mapValue(fm, "identity").orElse(Map.of());
    String agentName = stringValue(identity, "agent_name").orElse(null);
    String prompt = stringValue(identity, "prompt").orElse(null);

    Map<?, ?> provider =
        mapValue(fm, "provider")
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Agent [" + name + "] frontmatter 缺少 provider 段: " + agentFile));
    String providerName = stringValue(provider, "name").orElse(null);
    if (providerName == null || !providerNames.contains(providerName)) {
      throw new IllegalArgumentException(
          "Agent ["
              + name
              + "] 引用的 provider 不存在: "
              + providerName
              + "（全局层已声明: "
              + providerNames
              + "）");
    }
    String model = stringValue(provider, "model").orElse(null);
    Double temperature = doubleValue(provider, "temperature").orElse(null);

    List<String> tools = stringList(fm, "tools");
    List<String> mcpServers = stringList(fm, "mcp_servers");
    List<String> channels = channelNames(fm);
    List<String> bootstrap = stringList(fm, "bootstrap");
    List<Profile.Schedule> schedules = schedules(fm);

    Map<?, ?> settings = mapValue(fm, "settings").orElse(Map.of());
    Integer maxIterations = intValue(settings, "max_iterations").orElse(null);
    Integer maxHistoryTurns = intValue(settings, "max_history_turns").orElse(null);

    return new Profile(
        name,
        description,
        new Profile.Identity(agentName, prompt),
        new Profile.ProviderRef(providerName, model, temperature),
        tools,
        mcpServers,
        channels,
        schedules,
        bootstrap,
        new Profile.Settings(maxIterations, maxHistoryTurns));
  }

  /**
   * 扫 agents 根目录加载全部 Profile。单目录失败记错误日志并跳过，不阻断启动。 （失败目录的路径细节在异常消息/堆栈里，不单独作为日志参数，避免外部可控值直入日志。）
   *
   * @return name → Profile（仅校验通过的）
   */
  public Map<String, Profile> loadAll(Path agentsRoot, Set<String> providerNames) {
    Map<String, Profile> result = new LinkedHashMap<>();
    if (!Files.isDirectory(agentsRoot)) {
      return result;
    }
    try (Stream<Path> dirs = Files.list(agentsRoot)) {
      dirs.filter(Files::isDirectory)
          .sorted()
          .forEach(
              dir -> {
                try {
                  Profile profile = deriveProfile(dir, providerNames);
                  result.put(profile.name(), profile);
                } catch (RuntimeException e) {
                  LOG.error("Agent 加载失败，已跳过", e);
                }
              });
    } catch (IOException e) {
      LOG.error("扫描 agents 目录失败", e);
    }
    return result;
  }

  /** 解析 AGENT.md 的 frontmatter（首个 --- 分隔的 YAML 头）。 */
  private Map<String, Object> parseFrontmatter(Path agentFile) {
    try {
      String content = Files.readString(agentFile, StandardCharsets.UTF_8);
      int start = content.indexOf("---");
      if (start < 0) {
        throw new IllegalArgumentException("AGENT.md 缺少 frontmatter（--- 分隔的 YAML 头）: " + agentFile);
      }
      int end = content.indexOf("---", start + 3);
      if (end < 0) {
        throw new IllegalArgumentException("AGENT.md frontmatter 未闭合: " + agentFile);
      }
      String yamlText = content.substring(start + 3, end);
      Object loaded = new Yaml(new LoaderOptions()).load(yamlText);
      if (loaded == null) {
        return Map.of();
      }
      if (!(loaded instanceof Map<?, ?> rawMap)) {
        throw new IllegalArgumentException("AGENT.md frontmatter 不是合法 YAML 映射: " + agentFile);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) rawMap;
      return map;
    } catch (IOException e) {
      throw new IllegalArgumentException("读取 AGENT.md 失败: " + agentFile, e);
    }
  }

  private Optional<String> stringValue(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
  }

  private Optional<Map<?, ?>> mapValue(Map<?, ?> map, String key) {
    Object value = map.get(key);
    if (value instanceof Map<?, ?> nestedMap) {
      return Optional.of(nestedMap);
    }
    return Optional.empty();
  }

  private Optional<Integer> intValue(Map<?, ?> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return Optional.of(number.intValue());
    }
    return Optional.empty();
  }

  private Optional<Double> doubleValue(Map<?, ?> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return Optional.of(number.doubleValue());
    }
    return Optional.empty();
  }

  private List<String> stringList(Map<?, ?> map, String key) {
    Object value = map.get(key);
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        result.add(String.valueOf(item));
      }
      return result;
    }
    return List.of();
  }

  /** channels 支持 {@code - name: cli} 形态，提取 name 字段。 */
  private List<String> channelNames(Map<?, ?> map) {
    Object value = map.get("channels");
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> channelMap) {
        Object name = channelMap.get("name");
        if (name != null) {
          result.add(String.valueOf(name));
        }
      } else {
        result.add(String.valueOf(item));
      }
    }
    return result;
  }

  private List<Profile.Schedule> schedules(Map<?, ?> map) {
    Object value = map.get("schedules");
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Profile.Schedule> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> scheduleMap) {
        result.add(
            new Profile.Schedule(
                stringValue(scheduleMap, "cron").orElse(null),
                stringValue(scheduleMap, "zone").orElse(null),
                stringValue(scheduleMap, "message").orElse(null)));
      }
    }
    return Collections.unmodifiableList(result);
  }
}
