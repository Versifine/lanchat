package com.lanchat.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 集成测试环境助手：为每个测试类准备独立的临时 SQLite 库 + 临时上传目录，
 * 避免测试污染仓库根目录下的正式数据（data/lanchat.db、data/uploads）。
 *
 * <p>用法：测试类中
 * <pre>{@code
 * private static final IntegrationTestSupport.Env ENV = IntegrationTestSupport.createEnv();
 *
 * @DynamicPropertySource
 * static void props(DynamicPropertyRegistry registry) {
 *     IntegrationTestSupport.registerProperties(registry, ENV, "rest-it");
 * }
 * }</pre></p>
 */
public final class IntegrationTestSupport {

    private IntegrationTestSupport() {
    }

    /**
     * 独立测试环境：临时根目录、SQLite 库文件、上传目录。
     */
    public record Env(Path root, Path databaseFile, Path uploadDir) {

        /** 测试专用 SQLite 连接串：WAL + busy_timeout + 外键，与生产配置一致。 */
        public String jdbcUrl() {
            return "jdbc:sqlite:" + databaseFile + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on";
        }
    }

    /** 创建新的临时测试环境（库文件与上传目录都在系统临时目录下）。 */
    public static Env createEnv() {
        try {
            Path root = Files.createTempDirectory("lanchat-it-");
            return new Env(root, root.resolve("lanchat-test.db"), root.resolve("uploads"));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 把测试环境的 DataSource / 上传目录 / 日志文件覆盖进 Spring 环境。
     *
     * @param tag 日志文件名标识，避免多个测试上下文写同一个日志文件
     */
    public static void registerProperties(DynamicPropertyRegistry registry, Env env, String tag) {
        registry.add("spring.datasource.url", env::jdbcUrl);
        registry.add("lanchat.upload.dir", () -> env.uploadDir().toString());
        registry.add("logging.file.name", () -> "target/qa-test/logs/lanchat-" + tag + ".log");
    }
}
