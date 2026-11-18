package com.lanchat.application.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用层配置快照单元测试：历史条数规范化（契约：默认 50、最大 200）。
 */
class AppSettingsTest {

    private final AppSettings settings = new AppSettings(50, 20L * 1024 * 1024);

    @Test
    void normalizeHistoryLimitShouldFallbackToDefaultForNonPositive() {
        assertThat(settings.normalizeHistoryLimit(0)).isEqualTo(50);
        assertThat(settings.normalizeHistoryLimit(-1)).isEqualTo(50);
        assertThat(settings.normalizeHistoryLimit(-100)).isEqualTo(50);
    }

    @Test
    void normalizeHistoryLimitShouldKeepValidValue() {
        assertThat(settings.normalizeHistoryLimit(1)).isEqualTo(1);
        assertThat(settings.normalizeHistoryLimit(50)).isEqualTo(50);
        assertThat(settings.normalizeHistoryLimit(200)).isEqualTo(200);
    }

    @Test
    void normalizeHistoryLimitShouldCapAtContractMaximum() {
        assertThat(settings.normalizeHistoryLimit(201)).isEqualTo(200);
        assertThat(settings.normalizeHistoryLimit(999)).isEqualTo(200);
        assertThat(settings.normalizeHistoryLimit(Integer.MAX_VALUE)).isEqualTo(200);
    }

    @Test
    void contractConstantsShouldMatchSpec() {
        assertThat(AppSettings.MESSAGE_MAX_LENGTH).isEqualTo(2000);
        assertThat(AppSettings.HISTORY_MAX_LIMIT).isEqualTo(200);
    }
}
