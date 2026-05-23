package com.devops.dashboard.domain.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HostIdTest {

    @Test
    void shouldCreateHostIdFromString() {
        HostId hostId = HostId.of("pve-01");
        assertThat(hostId.value()).isEqualTo("pve-01");
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() -> HostId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");

        assertThatThrownBy(() -> HostId.of("  "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> HostId.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTrimValue() {
        HostId hostId = HostId.of("  vm-fedora-dev  ");
        assertThat(hostId.value()).isEqualTo("vm-fedora-dev");
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        HostId id1 = HostId.of("host-01");
        HostId id2 = HostId.of("host-01");
        HostId id3 = HostId.of("host-02");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void shouldHaveReadableToString() {
        HostId hostId = HostId.of("test-host");
        assertThat(hostId.toString()).contains("test-host");
    }
}
