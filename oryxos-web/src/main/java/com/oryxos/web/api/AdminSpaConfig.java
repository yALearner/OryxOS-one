package com.oryxos.web.api;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 管理台 SPA 回落（009-web-service FR-6）：{@code /admin/**} 静态资源从 {@code classpath:/static/admin/} 提供；
 * 未命中路径回落 {@code admin/index.html}——刷新子路由不 404（{@code GET /api/v1/**} 不受影响，路由优先级天然分开）。
 */
@Configuration
public class AdminSpaConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/admin/**")
        .addResourceLocations("classpath:/static/admin/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String resourcePath, Resource location)
                  throws IOException {
                Resource requested = location.createRelative(resourcePath);
                if (requested.exists() && requested.isReadable()) {
                  return requested;
                }
                return new ClassPathResource("/static/admin/index.html"); // SPA 回落
              }
            });
  }
}
