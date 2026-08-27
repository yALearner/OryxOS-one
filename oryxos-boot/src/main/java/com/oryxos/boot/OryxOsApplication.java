package com.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OryxOS 启动入口.
 *
 * <p>Spring Boot 主类，负责：
 *
 * <ul>
 *   <li>启动 Spring 容器
 *   <li>触发自动配置
 *   <li>聚合所有模块依赖
 * </ul>
 *
 * <p>启动模块聚合全部 8 个业务模块，组件分布在 {@code com.oryxos.*} 各包 （web 的 Controller / api 的
 * GlobalExceptionHandler、provider、memory 等）， 必须显式扩大扫描根到 {@code com.oryxos}，否则跨包组件不会被注册。
 */
@SpringBootApplication(scanBasePackages = "com.oryxos")
public class OryxOsApplication {

  public static void main(String[] args) {
    SpringApplication.run(OryxOsApplication.class, args);
  }
}
