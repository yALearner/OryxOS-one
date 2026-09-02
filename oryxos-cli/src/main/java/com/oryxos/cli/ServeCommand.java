package com.oryxos.cli;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/**
 * serve 命令（重命令，启动 Spring）——占位：Web Service 本体归第 26 节（课件 §三"serve 启动 Web Service（26 节细讲）"），本课启动
 * Spring 后输出占位提示并正常退出（用户拍板口径）。
 */
@Command(
    name = "serve",
    description = "启动 HTTP API 服务（Web 本体归第 26 节）",
    mixinStandardHelpOptions = true)
public class ServeCommand implements Runnable {

  @Override
  public void run() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(applicationClass())
            .web(WebApplicationType.NONE) // 占位阶段不起 Web 容器（Web 本体归第 26 节）
            .headless(true)
            .run()) {
      System.out.println("serve 的 Web 服务本体归第 26 节，当前为占位");
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
