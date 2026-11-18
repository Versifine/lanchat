package com.lanchat.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置（访问 http://localhost:8081/swagger-ui.html）。
 *
 * <p>注册 Bearer JWT 安全方案，配合 Controller 上的
 * {@code @SecurityRequirement(name = "bearerAuth")} 在 Swagger UI 中展示锁标记，
 * 可在页面右上角 Authorize 填入 token 后直接调试受保护接口。</p>
 */
@Configuration
public class OpenApiConfig {

    /** OpenAPI 安全方案名称，Controller 注解中引用。 */
    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI lanchatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LanChat API")
                        .version("v1")
                        .description("局域网聊天室后端接口：REST /api/** + WebSocket /ws/chat。"
                                + "错误响应统一为 {\"code\":<int>,\"message\":\"<中文说明>\"}，"
                                + "契约见 docs/api.md。"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("在 Authorize 中填入登录接口返回的 JWT token（无需加 Bearer 前缀）")));
    }
}
