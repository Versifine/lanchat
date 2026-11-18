package com.lanchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LanChat 服务端启动类。
 *
 * <p>架构：整洁架构启发（domain / application / adapter / infrastructure），
 * 依赖方向只允许由外向内。启动后访问 http://localhost:8081 即为前端页面，
 * 接口文档见 http://localhost:8081/swagger-ui.html。</p>
 */
@SpringBootApplication
public class LanChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(LanChatApplication.class, args);
    }
}
