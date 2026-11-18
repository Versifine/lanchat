package com.lanchat.infrastructure.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat 上传相关参数定制。
 *
 * <p>默认 maxSwallowSize 只有 2MB：客户端上传 21MB 文件时，服务端在超过
 * {@code spring.servlet.multipart.max-file-size} 后返回 413，但剩余请求体无法被“吞掉”，
 * 连接会被直接重置，客户端可能拿不到 413 响应。这里把可吞掉大小提高到 32MB，
 * 保证 EV-SEC-06（超大文件 → 413 / code 1006）稳定可观测。</p>
 */
@Configuration
public class TomcatUploadConfig {

    private static final int MAX_SWALLOW_SIZE_BYTES = 32 * 1024 * 1024;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> uploadAwareTomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                protocol.setMaxSwallowSize(MAX_SWALLOW_SIZE_BYTES);
            }
        });
    }
}
