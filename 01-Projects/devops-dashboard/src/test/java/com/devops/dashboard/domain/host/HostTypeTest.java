package com.devops.dashboard.domain.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HostTypeTest {

    @Test
    void shouldHaveAllExpectedTypes() {
        assertThat(HostType.values())
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        HostType.PVE_HYPERVISOR,
                        HostType.VM,
                        HostType.BARE_METAL,
                        HostType.LOCAL
                );
    }

    @Test
    void shouldConvertFromCode() {
        assertThat(HostType.fromCode("pve-hypervisor")).isEqualTo(HostType.PVE_HYPERVISOR);
        assertThat(HostType.fromCode("vm")).isEqualTo(HostType.VM);
        assertThat(HostType.fromCode("bare-metal")).isEqualTo(HostType.BARE_METAL);
        assertThat(HostType.fromCode("local")).isEqualTo(HostType.LOCAL);
    }

    @Test
    void shouldConvertFromCodeCaseInsensitive() {
        assertThat(HostType.fromCode("PVE-HYPERVISOR")).isEqualTo(HostType.PVE_HYPERVISOR);
        assertThat(HostType.fromCode("VM")).isEqualTo(HostType.VM);
    }

    @Test
    void shouldThrowOnInvalidCode() {
        assertThatThrownBy(() -> HostType.fromCode("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown HostType code");
    }

    @Test
    void shouldHaveDisplayName() {
        assertThat(HostType.PVE_HYPERVISOR.getDisplayName()).contains("PVE");
        assertThat(HostType.VM.getDisplayName()).contains("虚拟机");
    }
}
