package com.oryxos.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.channel.cli.CliChannel;
import com.oryxos.core.AgentService;
import com.oryxos.core.ContextLoader;
import com.oryxos.core.Profile;
import com.oryxos.core.ProfileLoader;
import com.oryxos.core.ProfileRegistry;
import com.oryxos.core.PromptBuilder;
import com.oryxos.core.ReActLoop;
import com.oryxos.core.SessionManager;
import com.oryxos.core.ToolExecutor;
import com.oryxos.core.ToolSchemaAdapter;
import com.oryxos.provider.ProviderProperties;
import com.oryxos.provider.ProviderService;
import com.oryxos.storage.SessionRepository;
import com.oryxos.storage.ToolInvocationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 重命令 Spring 装配（Spring 内部接线，非对外 API）——把 002 组件接成可运行整体（003-cli FR-10）。
 *
 * <p>落 oryxos-cli（CLAUDE.md 依赖方向：cli 组装所有模块）；轻命令不起 Spring 即不加载本类。{@code ProviderService} 由 001 的
 * ProviderConfiguration 自动装配；仓储/实体扫描由启动类显式声明（坑九，002 fix 已落）。
 *
 * <p>PromptBuilder 注入的工具集当前为空 Map——第 20 节 ToolRegistry 就位后替换（研究 R2 口径）。
 */
@Configuration
public class CliAgentConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(CliAgentConfiguration.class);

  @Bean
  public ProfileRegistry profileRegistry(Environment environment) {
    ProfileRegistry registry = new ProfileRegistry();
    Path agentsRoot = Path.of(".oryxos", "agents");
    if (!Files.isDirectory(agentsRoot)) {
      LOG.warn("工作区未初始化（先执行 oryxos init）: .oryxos/agents");
      return registry; // 空表：chat 时"Profile 未注册"清晰报错，不静默
    }
    Set<String> providerNames = providerNamesOf(environment);
    Map<String, Profile> profiles = new ProfileLoader().loadAll(agentsRoot, providerNames);
    profiles.values().forEach(registry::register);
    return registry;
  }

  @Bean
  public ContextLoader contextLoader() {
    return new ContextLoader(Path.of(".oryxos"));
  }

  @Bean
  public ToolSchemaAdapter toolSchemaAdapter(ObjectMapper objectMapper) {
    return new ToolSchemaAdapter(objectMapper);
  }

  @Bean
  public PromptBuilder promptBuilder(ContextLoader contextLoader, ToolSchemaAdapter adapter) {
    return new PromptBuilder(contextLoader, adapter, Map.of());
  }

  @Bean
  public ToolExecutor toolExecutor(ToolInvocationRepository repository, ObjectMapper objectMapper) {
    return new ToolExecutor(Map.of(), repository, objectMapper);
  }

  @Bean
  public ReActLoop reActLoop(
      ProviderService providerService, PromptBuilder promptBuilder, ToolExecutor toolExecutor) {
    return new ReActLoop(providerService, promptBuilder, toolExecutor);
  }

  @Bean
  public SessionManager sessionManager(
      SessionRepository sessionRepository, ObjectMapper objectMapper) {
    return new SessionManager(sessionRepository, objectMapper);
  }

  @Bean
  public AgentService agentService(
      ProfileRegistry registry, ReActLoop loop, SessionManager sessionManager) {
    return new AgentService(registry, loop, sessionManager);
  }

  @Bean
  public CliChannel cliChannel(AgentService agentService, SessionManager sessionManager) {
    return new CliChannel(agentService, sessionManager);
  }

  /** 全局层已声明 Provider 名集合（Profile 引用校验与 001 同口径）。 */
  private Set<String> providerNamesOf(Environment environment) {
    List<ProviderProperties> providers =
        Binder.get(environment)
            .bind("oryxos.providers", Bindable.listOf(ProviderProperties.class))
            .orElse(List.of());
    return providers.stream().map(ProviderProperties::getName).collect(Collectors.toSet());
  }
}
