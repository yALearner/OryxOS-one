package com.oryxos.cli;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/**
 * serve 命令（重命令，启动 Spring）——009-web-service FR-3：真启动 Web Service（8080，virtual
 * thread；application.yaml 已排除 Spring AI eager 装配——只认 DEEPSEEK_API_KEY 一个 key 就起得来）。常驻进程：定时任务（008
 * AgentScheduler）随 serve 并行运转 （CLAUDE.md「定时任务随 serve/gateway 常驻」契约）。
 */
@Command(
    name = "serve",
    description = "启动 HTTP API 服务（默认 8080，/api/v1 + /admin + /swagger-ui）",
    mixinStandardHelpOptions = true)
public class ServeCommand implements Runnable {

  @Override
  public void run() {
    try (ConfigurableApplicationContext ignored =
        new SpringApplicationBuilder(applicationClass())
            .web(WebApplicationType.SERVLET) // 009：真起 Web 容器（Web Service 本体）
            .headless(true)
            .run()) {
      // 常驻阻塞：Spring 上下文随 Web 容器运行；try-with-resources 关闭时随容器优雅收尾
    }
  }

  private Class<?> applicationClass() {
    try {
      return Class.forName("com.oryxos.boot.OryxOsApplication");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("未找到 Spring 启动类 com.oryxos.boot.OryxOsApplication", e);
    }
  }
}
