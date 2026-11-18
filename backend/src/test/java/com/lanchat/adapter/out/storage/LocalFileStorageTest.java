package com.lanchat.adapter.out.storage;

import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.model.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文件存储单元测试：流式落盘 + SHA-256、大小上限、目录穿越防护、UUID 落盘名
 * （EV-FUNC-12、EV-SEC-06、EV-SEC-07）。
 */
class LocalFileStorageTest {

    private static final long DEFAULT_LIMIT = 20L * 1024 * 1024;

    @TempDir
    Path tempDir;

    private Path uploadDir;
    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        uploadDir = tempDir.resolve("uploads");
        storage = new LocalFileStorage(uploadDir.toString());
    }

    @Test
    void storeShouldPersistBytesAndComputeSha256() throws IOException {
        byte[] data = new byte[10_000];
        new Random(42).nextBytes(data);

        StoredFile stored = storage.store(new ByteArrayInputStream(data), "report.pdf", DEFAULT_LIMIT);

        assertThat(stored.size()).isEqualTo(data.length);
        assertThat(stored.sha256()).isEqualTo(sha256Hex(data));
        assertThat(stored.storedName()).matches("^[0-9a-f]{32}(\\.[a-z0-9]+)?$");
        assertThat(uploadDir.resolve(stored.storedName())).isRegularFile();
        assertThat(readAll(storage.open(stored.storedName()))).isEqualTo(data);
    }

    @Test
    void storeShouldHandleZeroByteFile() throws IOException {
        StoredFile stored = storage.store(new ByteArrayInputStream(new byte[0]), "empty.txt", DEFAULT_LIMIT);

        assertThat(stored.size()).isZero();
        assertThat(stored.sha256()).isEqualTo(sha256Hex(new byte[0]));
        assertThat(readAll(storage.open(stored.storedName()))).isEmpty();
    }

    @Test
    void storeShouldRejectOversizedContentAndCleanTemporaryFiles() throws IOException {
        byte[] tooLarge = new byte[101];

        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(tooLarge), "big.bin", 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(1006));

        try (Stream<Path> files = Files.list(uploadDir)) {
            assertThat(files.toList()).as("超限后不应残留临时/目标文件").isEmpty();
        }
    }

    @Test
    void storeShouldExceedLimitOnlyWhenContentActuallyLarger() {
        byte[] exactlyAtLimit = new byte[100];

        assertThatCode(() -> storage.store(new ByteArrayInputStream(exactlyAtLimit), "ok.bin", 100L))
                .doesNotThrowAnyException();
    }

    @Test
    void diskNameShouldNeverContainUserPathSegments() {
        StoredFile stored = storage.store(new ByteArrayInputStream("x".getBytes()),
                "../../evil.sh", DEFAULT_LIMIT);

        assertThat(stored.storedName()).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
        assertThat(uploadDir.resolve(stored.storedName())).isRegularFile();
        assertThat(tempDir.resolve("evil.sh")).doesNotExist();
    }

    @Test
    void openShouldRejectPathTraversalAndMissingFile() {
        for (String name : new String[]{"../secret", "../../etc/passwd", "..\\..\\evil.sh", null, " ", "missing.bin"}) {
            assertThatThrownBy(() -> storage.open(name))
                    .as("打开非法/不存在文件应返回 1007: %s", name)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(1007));
        }
    }

    @Test
    void deleteQuietlyShouldRemoveFileAndIgnoreInvalidNames() throws IOException {
        StoredFile stored = storage.store(new ByteArrayInputStream("x".getBytes()), "a.txt", DEFAULT_LIMIT);
        Path path = uploadDir.resolve(stored.storedName());

        storage.deleteQuietly(stored.storedName());

        assertThat(path).doesNotExist();
        assertThatCode(() -> storage.deleteQuietly("../outside")).doesNotThrowAnyException();
        assertThatCode(() -> storage.deleteQuietly(null)).doesNotThrowAnyException();
    }

    @Test
    void uploadDirectoryShouldContainOnlyGeneratedNames() throws IOException {
        List<String> originals = List.of("../../evil.sh", "a b.txt", "x/y/z.pdf", "..\\..\\w.bat");
        for (String original : originals) {
            storage.store(new ByteArrayInputStream("data".getBytes()), original, DEFAULT_LIMIT);
        }

        try (Stream<Path> files = Files.list(uploadDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .allMatch(name -> name.matches("^[0-9a-f]{32}(\\.[a-z0-9]+)?$"));
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (input) {
            return input.readAllBytes();
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
