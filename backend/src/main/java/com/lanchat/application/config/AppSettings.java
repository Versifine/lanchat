package com.lanchat.application.config;

/**
 * 应用层需要的业务配置快照。
 *
 * <p>由 infrastructure 层从 application.yml（{@code lanchat.*}）绑定后注入，
 * 这样应用层不必直接依赖 Spring 的 {@code @Value} / {@code @ConfigurationProperties}。
 * 业务硬约束（契约规定的 2000 字符消息、最多 200 条历史）作为常量放在这里。</p>
 *
 * @param historyDefaultLimit 历史消息默认条数（application.yml: lanchat.history.limit）
 * @param uploadMaxBytes      文件大小上限（application.yml: lanchat.upload.max-size-bytes）
 */
public record AppSettings(int historyDefaultLimit, long uploadMaxBytes) {

    /** 单条消息最大字符数（契约：消息 ≤2000 字符）。 */
    public static final int MESSAGE_MAX_LENGTH = 2000;

    /** 历史消息最大条数（契约：limit 最大 200）。 */
    public static final int HISTORY_MAX_LIMIT = 200;

    /** 把请求里的 limit 参数规范化到 [1, 200]；非法值回退到默认条数。 */
    public int normalizeHistoryLimit(int requested) {
        if (requested <= 0) {
            return historyDefaultLimit;
        }
        return Math.min(requested, HISTORY_MAX_LIMIT);
    }
}
