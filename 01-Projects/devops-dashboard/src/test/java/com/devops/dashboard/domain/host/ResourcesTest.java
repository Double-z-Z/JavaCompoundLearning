package com.devops.dashboard.domain.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourcesTest {

    @Test
    void shouldCalculateCpuUtilization() {
        Resources resources = Resources.builder()
                .cpuTotal(8)
                .cpuFree(4)
                .memTotalMb(16384)
                .memFreeMb(8192)
                .build();

        assertThat(resources.cpuUtilizationPercent()).isEqualTo(50.0);
        assertThat(resources.memUtilizationPercent()).isEqualTo(50.0);
    }

    @Test
    void shouldCheckResourceAvailability() {
        Resources resources = Resources.builder()
                .cpuTotal(8)
                .cpuFree(6)
                .memTotalMb(16384)
                .memFreeMb(12000)
                .build();

        assertThat(resources.canAccommodate(4, 4096)).isTrue();
        assertThat(resources.canAccommodate(8, 16000)).isFalse();
    }

    @Test
    void shouldHandleZeroTotalGracefully() {
        Resources resources = Resources.builder()
                .cpuTotal(0)
                .cpuFree(0)
                .memTotalMb(0)
                .memFreeMb(0)
                .build();

        assertThat(resources.cpuUtilizationPercent()).isEqualTo(0.0);
        assertThat(resources.memUtilizationPercent()).isEqualTo(0.0);
    }

    @Test
    void shouldRejectNegativeValues() {
        assertThatThrownBy(() -> Resources.builder()
                .cpuTotal(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Resources.builder()
                .memTotalMb(-100)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
