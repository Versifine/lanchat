package com.lanchat.infrastructure.config;

import com.lanchat.adapter.in.web.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置：
 * <ul>
 *   <li>CORS：允许局域网内跨源访问（开发态 Vite 5173 走代理，生产态同源；保留 CORS 便于直连调试）；</li>
 *   <li>注册 {@code @CurrentUser} 参数解析器，把 JWT 过滤器解析出的登录身份注入 Controller。</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
