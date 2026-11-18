package com.lanchat.application.service;

import com.lanchat.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传文件名清洗单元测试（SEC-05 / EV-SEC-07）。
 */
class FileNameCleanerTest {

    // ------------------------------------------------------------------ sanitize

    @Test
    void sanitizeShouldStripDirectoryTraversalPaths() {
        assertThat(FileNameCleaner.sanitize("../../evil.sh")).isEqualTo("evil.sh");
        assertThat(FileNameCleaner.sanitize("..\\..\\evil.sh")).isEqualTo("evil.sh");
        assertThat(FileNameCleaner.sanitize("/etc/passwd")).isEqualTo("passwd");
        assertThat(FileNameCleaner.sanitize("C:\\Windows\\system32\\cmd.exe")).isEqualTo("cmd.exe");
        assertThat(FileNameCleaner.sanitize("dir/subdir/report.pdf")).isEqualTo("report.pdf");
    }

    @Test
    void sanitizeShouldRemoveAllDotDotFragments() {
        // 路径段被剥离后仍可能残留 ".." 片段，必须替换掉
        assertThat(FileNameCleaner.sanitize("..foo..bar.txt")).doesNotContain("..");
        assertThat(FileNameCleaner.sanitize("....")).doesNotContain("..");
    }

    @Test
    void sanitizeShouldReplaceIllegalAndControlCharacters() {
        assertThat(FileNameCleaner.sanitize("a:b*c?d\"e<f>g|h.txt")).isEqualTo("a_b_c_d_e_f_g_h.txt");
        assertThat(FileNameCleaner.sanitize("bad\u0000name\u0001.txt")).isEqualTo("bad_name_.txt");
        assertThat(FileNameCleaner.sanitize("tab\there.txt")).isEqualTo("tab_here.txt");
        assertThat(FileNameCleaner.sanitize("new\nline.txt")).isEqualTo("new_line.txt");
    }

    @Test
    void sanitizeShouldStripLeadingDotsAndWhitespace() {
        assertThat(FileNameCleaner.sanitize("  report.pdf  ")).isEqualTo("report.pdf");
        assertThat(FileNameCleaner.sanitize(".hidden")).isEqualTo("hidden");
        // 注意清洗顺序：先删 ".." 片段再删开头点，因此 "...secret.txt" -> "_.secret.txt"
        // （安全不变式：不含路径分隔符、不以 ".." 逃逸、不是隐藏文件前缀点）
        assertThat(FileNameCleaner.sanitize("...secret.txt")).isEqualTo("_.secret.txt");
    }

    @Test
    void sanitizeShouldFallbackToUnnamedForEmptyInput() {
        assertThat(FileNameCleaner.sanitize(null)).isEqualTo("unnamed");
        assertThat(FileNameCleaner.sanitize("")).isEqualTo("unnamed");
        assertThat(FileNameCleaner.sanitize("   ")).isEqualTo("unnamed");
        assertThat(FileNameCleaner.sanitize("/")).isEqualTo("unnamed");
        assertThat(FileNameCleaner.sanitize("\\")).isEqualTo("unnamed");
        // 纯点文件名会先被替换为下划线，仍是非空安全名（无路径分隔、无 ".."）
        for (String dots : new String[]{"...", "....", ".. ."}) {
            String safe = FileNameCleaner.sanitize(dots);
            assertThat(safe).isNotEmpty().doesNotContain("/").doesNotContain("\\").doesNotContain("..");
        }
    }

    @Test
    void sanitizeShouldShortenOverlongNameButKeepSafeExtension() {
        String longBase = "a".repeat(300);
        String shortened = FileNameCleaner.sanitize(longBase + ".txt");

        assertThat(shortened).hasSize(120);
        assertThat(shortened).endsWith(".txt");
        assertThat(shortened).startsWith("a");
    }

    @Test
    void sanitizeShouldShortenOverlongNameWithoutExtension() {
        String shortened = FileNameCleaner.sanitize("b".repeat(300));

        assertThat(shortened).hasSize(120);
    }

    @Test
    void sanitizeShouldNeverContainPathSeparators() {
        List<String> inputs = List.of("../../evil.sh", "..\\..\\evil.sh", "/etc/passwd",
                "a/b\\c", "..%2F..%2Fetc%2Fpasswd", ".\\..\\x");
        for (String input : inputs) {
            String safe = FileNameCleaner.sanitize(input);
            assertThat(safe).as("清洗结果不得包含路径分隔符: %s", input)
                    .doesNotContain("/").doesNotContain("\\");
            assertThat(safe).doesNotStartWith(".").doesNotContain("..");
        }
    }

    // ------------------------------------------------------------- safeExtension

    @Test
    void safeExtensionShouldBeLowercaseAlphanumericOrEmpty() {
        // 安全不变式：返回空串或 “. + 1..12 位小写字母数字”，绝不包含路径/特殊字符
        List<String> names = List.of("report.pdf", "a.tar.gz", "noext", ".bashrc", "a.",
                "a.toolongextension123", "x.ABC", "x.a-b", "x.a b", "archive.ABCDEFGHIJKL");
        for (String name : names) {
            assertThat(FileNameCleaner.safeExtension(name))
                    .as("扩展名安全不变式: %s", name)
                    .matches("|\\.[a-z0-9]{1,12}");
        }
    }

    @Test
    void safeExtensionShouldAcceptExactlyTwelveAlphanumericChars() {
        assertThat(FileNameCleaner.safeExtension("archive.ABCDEFGHIJKL")).isEqualTo(".abcdefghijkl");
    }

    @Test
    void safeExtensionShouldHandleNullAndDegenerateNames() {
        assertThat(FileNameCleaner.safeExtension(null)).isEmpty();
        assertThat(FileNameCleaner.safeExtension("noext")).isEmpty();
        assertThat(FileNameCleaner.safeExtension(".bashrc")).isEmpty();
        assertThat(FileNameCleaner.safeExtension("trailing.")).isEmpty();
    }

    // ------------------------------------------------------------- requireValid

    @Test
    void requireValidShouldAcceptSanitizedName() {
        assertThatCode(() -> FileNameCleaner.requireValid("report.pdf")).doesNotThrowAnyException();
        assertThatCode(() -> FileNameCleaner.requireValid("a".repeat(120))).doesNotThrowAnyException();
    }

    @Test
    void requireValidShouldRejectNullBlankAndOverlongName() {
        for (String invalid : new String[]{null, "", "   ", "a".repeat(121)}) {
            assertThatThrownBy(() -> FileNameCleaner.requireValid(invalid))
                    .as("非法文件名应抛 1004: %s", invalid)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(1004));
        }
    }
}
