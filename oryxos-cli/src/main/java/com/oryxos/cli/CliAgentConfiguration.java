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
import com.oryxos.storage.NotifyChannelRepository;
import com.oryxos.storage.SessionRepository;
import com.oryxos.storage.ToolInvocationRepository;
import com.oryxos.tool.AnnotatedMethodToolAdapter;
import com.oryxos.tool.PermissiveSandbox;
import com.oryxos.tool.Sandbox;
import com.oryxos.tool.ToolRegistry;
import com.oryxos.tool.builtin.HttpGetTool;
import com.oryxos.tool.builtin.HttpPostTool;
import com.oryxos.tool.builtin.ListDirTool;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ReadFileTool;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.builtin.WriteFileTool;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.NotifyChannelRegistry;
import com.oryxos.tool.notify.WebhookNotifyAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * 重命令 Spring 装配（Spring 内部接线，非对外 API）——把 002 组件接成可运行整体（003-cli FR-10）； 005-tool FR-7：工具集空 Map 换成
 * ToolRegistry 全量、004 遗留 NotifyTools 接线（契约不变量 9）、 PermissiveSandbox 临时接线（24 节替换）、Profile tools
 * 引用启动校验（001 同款纪律）。
 *
 * <p>落 oryxos-cli（CLAUDE.md 依赖方向：cli 组装所有模块）；轻命令不起 Spring 即不加载本类。{@code ProviderService} 由 001 的
 * ProviderConfiguration 自动装配；仓储/实体扫描由启动类显式声明（坑九，002 fix 已落）。
 *
 * <p>core 零改动：PromptBuilder/ToolExecutor 仍注入按名 Map（全量），PromptBuilder.selectTools 按 Profile 过滤 沿用
 * 002 现有路径（未知名 WARN 兜底）；本处启动校验在其之上补充 ERROR 级明确报错。
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

  /**
   * 临时全放行 Sandbox（005-tool 拍板方案 A）——**第 24 节替换为 WhitelistSandbox（002 FR-7）**，只换本 Bean
   * 实现类，调用方零改动。20~23 节白名单未生效：内网假设 + 审计留痕 + 保守 Profile 纪律兜底（需求文档 FR-7）。
   */
  @Bean
  public Sandbox sandbox() {
    return new PermissiveSandbox();
  }

  /** 004 契约不变量 9：Boot 自动配置 factory + connect/read timeout（connect 3s / read 10s）。 */
  @Bean
  public RestClient restClient() {
    var settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(3))
            .withReadTimeout(Duration.ofSeconds(10));
    return RestClient.builder()
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .build();
  }

  /**
   * 统一工具注册表：内置六件 + NotifyTools（004 接线）+ 方式三/MCP（其 @Component 自注册）。 依赖 profileRegistry 完成 Profile
   * tools 引用启动校验（001 同款纪律：明确报错不静默； 校验失败记录 ERROR、不阻断启动——与 001 provider 引用校验口径一致）。
   */
  @Bean
  public ToolRegistry toolRegistry(
      ProfileRegistry profileRegistry,
      Sandbox sandbox,
      RestClient restClient,
      NotifyTools notifyTools,
      ObjectProvider<MethodToolCallbackProvider> methodProvider,
      ObjectMapper objectMapper) {
    ToolRegistry registry = new ToolRegistry();
    registry.register(new ReadFileTool(sandbox));
    registry.register(new WriteFileTool(sandbox));
    registry.register(new ListDirTool(sandbox));
    registry.register(new ShellTools(sandbox, 30_000));
    registry.register(new HttpGetTool(sandbox, restClient));
    registry.register(new HttpPostTool(sandbox, restClient));
    registry.register(notifyTools);
    registerAnnotatedMethodTools(registry, methodProvider, objectMapper);
    validateToolRefs(profileRegistry, registry);
    return registry;
  }

  /**
   * 方式三接线（FR-6/FR-7）：扫描容器内 @Tool 方法、包装成 OryxTool 注册（仅借 Spring AI 扫描与 schema 生成——坑二，执行走
   * ToolExecutor）。Provider bean 不可用（Spring AI 自动配置未生效）→ 记录 INFO 跳过 （FR-6 降级路径：业务方改为装配处手动注册）。
   */
  @Bean
  public MethodToolCallbackProvider methodToolCallbackProvider(ApplicationContext context) {
    List<Object> toolBeans = new java.util.ArrayList<>();
    for (String name : context.getBeanDefinitionNames()) {
      Class<?> type;
      try {
        type = context.getType(name);
      } catch (Exception e) {
        continue;
      }
      if (type == null || !hasToolAnnotatedMethod(type)) {
        continue;
      }
      toolBeans.add(context.getBean(name));
    }
    if (toolBeans.isEmpty()) {
      return null; // 无 @Tool bean 是合法空态——registerAnnotatedMethodTools 走 INFO 跳过
    }
    return MethodToolCallbackProvider.builder().toolObjects(toolBeans.toArray()).build();
  }

  private static boolean hasToolAnnotatedMethod(Class<?> type) {
    for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Tool.class)) {
        return true;
      }
    }
    return false;
  }

  private void registerAnnotatedMethodTools(
      ToolRegistry registry,
      ObjectProvider<MethodToolCallbackProvider> methodProvider,
      ObjectMapper objectMapper) {
    MethodToolCallbackProvider provider = methodProvider.getIfAvailable();
    if (provider == null) {
      LOG.info("MethodToolCallbackProvider 不可用——方式三 @Tool 扫描跳过（降级：装配处手动注册）");
      return;
    }
    for (var callback : provider.getToolCallbacks()) {
      if (callback instanceof MethodToolCallback methodCallback) {
        registry.register(new AnnotatedMethodToolAdapter(methodCallback, objectMapper));
      }
    }
  }

  @Bean
  public PromptBuilder promptBuilder(
      ContextLoader contextLoader, ToolSchemaAdapter adapter, ToolRegistry registry) {
    return new PromptBuilder(contextLoader, adapter, nameMapOf(registry));
  }

  @Bean
  public ToolExecutor toolExecutor(
      ToolInvocationRepository repository, ObjectMapper objectMapper, ToolRegistry registry) {
    return new ToolExecutor(nameMapOf(registry), repository, objectMapper);
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

  /** 004 遗留接线：NotifyChannelRegistry（真实 Repository）+ adapter 显式映射（宪法 III 哲学）。 */
  @Bean
  public NotifyTools notifyTools(
      Sandbox sandbox, RestClient restClient, NotifyChannelRepository repository) {
    Map<String, NotifyChannelAdapter> adapters =
        Map.of("webhook", new WebhookNotifyAdapter(restClient));
    return new NotifyTools(sandbox, adapters, new NotifyChannelRegistry(repository));
  }

  /** core 零改动：全量按名 Map 注入 PromptBuilder/ToolExecutor（002 现有路径）。 */
  private Map<String, com.oryxos.core.OryxTool> nameMapOf(ToolRegistry registry) {
    return registry.all().stream()
        .collect(
            Collectors.toUnmodifiableMap(com.oryxos.core.OryxTool::getName, Function.identity()));
  }

  /** Profile tools 引用启动校验（FR-1 自审补钉）：未注册名 ERROR 级明确报错、不静默少一个。 */
  private void validateToolRefs(ProfileRegistry profileRegistry, ToolRegistry registry) {
    for (Profile profile : profileRegistry.list()) {
      List<String> declared = profile.tools();
      if (declared == null) {
        continue;
      }
      for (String name : declared) {
        if (!registry.contains(name)) {
          // 名称入日志前净化 CR/LF（CRLF 注入防线，无抑制注解依赖的机器可判写法）
          LOG.error("Profile [{}] 声明的工具未注册: {}", sanitize(profile.name()), sanitize(name));
        }
      }
    }
  }

  private String sanitize(String value) {
    return value.replace('\r', '?').replace('\n', '?');
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
