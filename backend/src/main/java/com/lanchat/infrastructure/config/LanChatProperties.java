package com.lanchat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 业务配置绑定（前缀 {@code lanchat.*}，见 application.yml）。
 *
 * <p>用 record + 构造器绑定；本类属于 infrastructure 层，应用层只接收
 * {@link com.lanchat.application.config.AppSettings} 这类纯 Java 快照，不直接依赖 Spring 配置机制。</p>
 */
@ConfigurationProperties(prefix = "lanchat")
public record LanChatProperties(Jwt jwt,
                                Upload upload,
                                History history,
                                Heartbeat heartbeat,
                                RateLimit rateLimit) {

    /** JWT 配置：HS256 密钥须 ≥32 字节（SEC-02），有效期默认 24 小时。 */
    public record Jwt(@DefaultValue("lanchat-dev-secret-key-please-change-32bytes-min") String secret,
                      @DefaultValue("24") long ttlHours) {
    }

    /** 上传配置：固定上传目录 + 单文件字节上限（默认 20MB）。 */
    public record Upload(@DefaultValue("data/uploads") String dir,
                         @DefaultValue("20971520") long maxSizeBytes) {
    }

    /** 历史消息配置：默认拉取条数。 */
    public record History(@DefaultValue("50") int limit) {
    }

    /** 心跳配置：服务端超过该时长未收到任何 WS 消息即判定离线（毫秒）。 */
    public record Heartbeat(@DefaultValue("35000") long timeoutMs) {
    }

    /** 限流配置：登录失败次数/分钟、消息条数/秒。 */
    public record RateLimit(@DefaultValue("5") int loginFailPerMinute,
                            @DefaultValue("20") int messagesPerSecond) {
    }
}
