package com.lanchat.application.service;

import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.User;

import java.util.regex.Pattern;

/**
 * 上传文件名清洗工具（SEC-05 / EV-SEC-07）。
 *
 * <p>清洗规则：</p>
 * <ol>
 *   <li>只取路径最后一段（同时兼容 {@code /} 与 {@code \\}），防止目录穿越；</li>
 *   <li>把所有 {@code ..} 片段替换为 {@code _}；</li>
 *   <li>非法字符（控制字符、{@code \\ / : * ? " < > |}）替换为 {@code _}；</li>
 *   <li>去掉首尾空白与开头的点，空名回退为 {@code unnamed}；</li>
 *   <li>超长时保留扩展名并截断主文件名。</li>
 * </ol>
 *
 * <p>真正落盘时还会使用 UUID 生成的新文件名，原始文件名只作为数据库展示字段，
 * 因此即使清洗不完美也不会逃逸上传目录。</p>
 */
public final class FileNameCleaner {

    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_EXTENSION_LENGTH = 12;
    private static final String FALLBACK_NAME = "unnamed";
    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[A-Za-z0-9]{" + MAX_EXTENSION_LENGTH + "}");
    private static final Pattern ALNUM_EXTENSION = Pattern.compile("[A-Za-z0-9]+");

    private FileNameCleaner() {
    }

    /**
     * 清洗客户端上传的原始文件名。
     *
     * @return 可安全展示、可用作扩展名来源的文件名；输入为 null/空白时返回 {@code unnamed}
     */
    public static String sanitize(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return FALLBACK_NAME;
        }
        String name = rawFileName.replace('\\', '/');
        int slashIndex = name.lastIndexOf('/');
        if (slashIndex >= 0) {
            name = name.substring(slashIndex + 1);
        }
        name = ILLEGAL_CHARS.matcher(name).replaceAll("_");
        while (name.contains("..")) {
            name = name.replace("..", "_");
        }
        name = name.strip();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return FALLBACK_NAME;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = shorten(name);
        }
        return name;
    }

    /**
     * 推断安全扩展名（含点，例如 {@code ".pdf"}）；没有安全扩展名时返回空字符串。
     */
    public static String safeExtension(String safeName) {
        if (safeName == null) {
            return "";
        }
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) {
            return "";
        }
        String extension = safeName.substring(dot + 1);
        if (extension.length() > MAX_EXTENSION_LENGTH || !SAFE_EXTENSION.matcher(extension).matches()) {
            return "";
        }
        return "." + extension.toLowerCase();
    }

    /**
     * 校验清洗后的文件名是否合法（防御性检查，正常清洗后总能通过）。
     *
     * @throws BusinessException 文件名非法时返回错误码 1004
     */
    public static void requireValid(String safeName) {
        if (safeName == null || safeName.isBlank() || safeName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "文件名非法");
        }
    }

    private static String shorten(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1);
            if (extension.length() <= MAX_EXTENSION_LENGTH && ALNUM_EXTENSION.matcher(extension).matches()) {
                int keep = Math.max(1, MAX_NAME_LENGTH - extension.length() - 1);
                return name.substring(0, keep) + "." + extension;
            }
        }
        return name.substring(0, MAX_NAME_LENGTH);
    }
}
