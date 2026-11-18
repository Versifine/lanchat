package com.lanchat.infrastructure.config;

import com.lanchat.adapter.out.storage.LocalFileStorage;
import com.lanchat.application.config.AppSettings;
import com.lanchat.application.service.AuthService;
import com.lanchat.application.service.ChatService;
import com.lanchat.application.service.FileService;
import com.lanchat.application.service.UserService;
import com.lanchat.domain.port.FileRepository;
import com.lanchat.domain.port.FileStorage;
import com.lanchat.domain.port.LoginAttemptLimiter;
import com.lanchat.domain.port.MessageRateLimiter;
import com.lanchat.domain.port.MessageRepository;
import com.lanchat.domain.port.PasswordHasher;
import com.lanchat.domain.port.RealtimeNotifier;
import com.lanchat.domain.port.TokenIssuer;
import com.lanchat.domain.port.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用装配配置。
 *
 * <p>应用层服务是不带 Spring 注解的纯 Java 类（业务不依赖框架、可单测），
 * 统一在这里用 {@code @Bean} 装配；同时把 {@code lanchat.*} 配置转换为应用层快照
 * {@link AppSettings}，避免 Spring 配置类型泄漏进 application 层。</p>
 */
@Configuration
@EnableConfigurationProperties(LanChatProperties.class)
public class LanChatConfig {

    @Bean
    public AppSettings appSettings(LanChatProperties properties) {
        return new AppSettings(properties.history().limit(), properties.upload().maxSizeBytes());
    }

    @Bean
    public FileStorage fileStorage(LanChatProperties properties) {
        return new LocalFileStorage(properties.upload().dir());
    }

    @Bean
    public AuthService authService(UserRepository userRepository,
                                   PasswordHasher passwordHasher,
                                   TokenIssuer tokenIssuer,
                                   LoginAttemptLimiter loginAttemptLimiter) {
        return new AuthService(userRepository, passwordHasher, tokenIssuer, loginAttemptLimiter);
    }

    @Bean
    public UserService userService(UserRepository userRepository, RealtimeNotifier realtimeNotifier) {
        return new UserService(userRepository, realtimeNotifier);
    }

    @Bean
    public ChatService chatService(MessageRepository messageRepository,
                                   UserRepository userRepository,
                                   RealtimeNotifier realtimeNotifier,
                                   MessageRateLimiter messageRateLimiter,
                                   AppSettings appSettings) {
        return new ChatService(messageRepository, userRepository, realtimeNotifier, messageRateLimiter, appSettings);
    }

    @Bean
    public FileService fileService(FileRepository fileRepository,
                                   UserRepository userRepository,
                                   FileStorage fileStorage,
                                   RealtimeNotifier realtimeNotifier,
                                   AppSettings appSettings) {
        return new FileService(fileRepository, userRepository, fileStorage, realtimeNotifier, appSettings);
    }
}
