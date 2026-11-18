-- LanChat SQLite 数据库结构（启动时由 spring.sql.init 自动执行，全部幂等）
-- 时间戳统一使用 epoch 毫秒

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    nickname      TEXT    NOT NULL,
    password_hash TEXT    NOT NULL,           -- PBKDF2WithHmacSHA256 十六进制
    salt          TEXT    NOT NULL,           -- 16 字节盐的十六进制
    created_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_id   INTEGER NOT NULL,
    receiver_id INTEGER,                       -- NULL 表示群聊
    content     TEXT    NOT NULL,
    created_at  INTEGER NOT NULL,
    delivered   INTEGER NOT NULL DEFAULT 0,    -- 私聊：0=未投递（离线待补发）1=已投递
    FOREIGN KEY (sender_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_messages_created ON messages (created_at);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_delivered
    ON messages (receiver_id, delivered);

CREATE TABLE IF NOT EXISTS files (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    uploader_id   INTEGER NOT NULL,
    original_name TEXT    NOT NULL,
    stored_name   TEXT    NOT NULL UNIQUE,     -- 磁盘上的安全文件名
    size          INTEGER NOT NULL,
    sha256        TEXT    NOT NULL,
    created_at    INTEGER NOT NULL,
    FOREIGN KEY (uploader_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_files_uploader ON files (uploader_id);
