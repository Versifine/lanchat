package com.lanchat.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动早期创建 SQLite 数据目录与文件上传目录。
 *
 * <p>背景：sqlite-jdbc 不会自动创建数据库文件的父目录，配置 {@code jdbc:sqlite:data/lanchat.db}
 * 时若 {@code data/} 不存在会直接启动失败。这里用 {@link BeanFactoryPostProcessor}
 * 在任何普通 Bean（尤其是 DataSource 与 spring.sql.init 的建表脚本）初始化之前执行，
 * 从 Environment 读取配置并幂等创建目录，保证 IDE / Maven / jar 三种启动方式一致。</p>
 */
@Configuration
public class StorageBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageBootstrapConfig.class);
    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

    @Bean
    public static BeanFactoryPostProcessor storageDirectoryInitializer(Environment environment) {
        return beanFactory -> {
            String datasourceUrl = environment.getProperty("spring.datasource.url", "jdbc:sqlite:data/lanchat.db");
            Path databaseFile = resolveSqliteDatabasePath(datasourceUrl);
            if (databaseFile != null) {
                createParentDirectory(databaseFile, "SQLite 数据目录");
            }
            String uploadDirectory = environment.getProperty("lanchat.upload.dir", "data/uploads");
            createDirectory(Path.of(uploadDirectory), "文件上传目录");
        };
    }

    /**
     * 从 {@code jdbc:sqlite:data/lanchat.db?journal_mode=WAL...} 中解析数据库文件路径。
     */
    private static Path resolveSqliteDatabasePath(String datasourceUrl) {
        if (datasourceUrl == null || !datasourceUrl.startsWith(SQLITE_URL_PREFIX)) {
            return null;
        }
        String path = datasourceUrl.substring(SQLITE_URL_PREFIX.length());
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (path.isBlank() || ":memory:".equals(path) || path.startsWith("file:")) {
            return null;
        }
        return Path.of(path);
    }

    private static void createParentDirectory(Path file, String description) {
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            createDirectory(parent, description);
        }
    }

    private static void createDirectory(Path directory, String description) {
        try {
            if (Files.isDirectory(directory)) {
                return;
            }
            Files.createDirectories(directory);
            log.info("已创建{}: {}", description, directory.toAbsolutePath().normalize());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建" + description + ": " + directory, e);
        }
    }
}
