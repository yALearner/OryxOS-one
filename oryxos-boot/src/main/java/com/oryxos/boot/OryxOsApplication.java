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
 */
@SpringBootApplication
public class OryxOsApplication {

  public static void main(String[] args) {
    SpringApplication.run(OryxOsApplication.class, args);
  }
}
