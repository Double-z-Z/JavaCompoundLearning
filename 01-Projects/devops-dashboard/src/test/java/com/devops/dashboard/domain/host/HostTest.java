package com.devops.dashboard.domain.host;

import com.devops.dashboard.domain.loadgen.LoadgenTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class HostTest {

    private Host targetHost;
    private Host loadgenHost;
    private Host mcpHost;

    @BeforeEach
    void setUp() {
        targetHost = Host.builder()
                .id(HostId.of("vm-ubuntu-test"))
                .type(HostType.VM)
                .parentId(HostId.of("pve-01"))
                .label("Ubuntu 测试环境")
                .networkZone("lan-10.0.0")
                .capabilities(Set.of(Capability.DOCKER, Capability.NATIVE))
                .roles(Set.of(HostRole.TARGET))
                .resources(Resources.builder()
                        .cpuTotal(4)
                        .cpuFree(3)
                        .memTotalMb(8192)
                        .memFreeMb(6144)
                        .build())
                .access(HostAccess.builder()
                        .sshHost("10.0.0.103")
                        .sshPort(22)
                        .user("test")
                        .keyPath("/home/dev/.ssh/id_rsa")
                        .build())
                .build();

        loadgenHost = Host.builder()
                .id(HostId.of("vm-loadgen-01"))
                .type(HostType.VM)
                .parentId(HostId.of("pve-01"))
                .label("专用压测机")
                .networkZone("lan-10.0.0")
                .capabilities(Set.of(Capability.NATIVE))
                .roles(Set.of(HostRole.LOADGEN))
                .resources(Resources.builder()
                        .cpuTotal(8)
                        .cpuFree(8)
                        .memTotalMb(8192)
                        .memFreeMb(8192)
                        .build())
                .loadgenTools(Set.of(LoadgenTool.WRK, LoadgenTool.HEY, LoadgenTool.AB))
                .build();

        mcpHost = Host.builder()
                .id(HostId.of("vm-fedora-dev"))
                .type(HostType.VM)
                .parentId(HostId.of("pve-01"))
                .label("Fedora 开发环境")
                .capabilities(Set.of(Capability.DOCKER, Capability.NATIVE))
                .roles(Set.of(HostRole.MCP_HOST, HostRole.TARGET))
                .build();
    }

    @Test
    void shouldIdentifyTargetRole() {
        assertThat(targetHost.isTarget()).isTrue();
        assertThat(targetHost.isLoadgen()).isFalse();
        assertThat(targetHost.isMcpHost()).isFalse();
    }

    @Test
    void shouldIdentifyLoadgenRole() {
        assertThat(loadgenHost.isLoadgen()).isTrue();
        assertThat(loadgenHost.isTarget()).isFalse();
    }

    @Test
    void shouldIdentifyMcpHostRole() {
        assertThat(mcpHost.isMcpHost()).isTrue();
        assertThat(mcpHost.isTarget()).isTrue();
    }

    @Test
    void shouldCheckDockerCapability() {
        assertThat(targetHost.supportsDocker()).isTrue();
        assertThat(loadgenHost.supportsDocker()).isFalse();
    }

    @Test
    void shouldCheckLoadgenTools() {
        assertThat(loadgenHost.hasLoadgenTool(LoadgenTool.WRK)).isTrue();
        assertThat(loadgenHost.hasLoadgenTool(LoadgenTool.HEY)).isTrue();
        assertThat(targetHost.hasLoadgenTool(LoadgenTool.WRK)).isFalse();
    }

    @Test
    void shouldDetermineRootNode() {
        Host pveHost = Host.builder()
                .id(HostId.of("pve-01"))
                .type(HostType.PVE_HYPERVISOR)
                .label("PVE 宿主机")
                .capabilities(Set.of(Capability.VM))
                .build();

        assertThat(pveHost.isRoot()).isTrue();
        assertThat(targetHost.isRoot()).isFalse();
    }

    @Test
    void shouldDetectSiblingRelationship() {
        assertThat(targetHost.isSibling(loadgenHost)).isTrue();

        Host otherParentHost = Host.builder()
                .id(HostId.of("other-vm"))
                .type(HostType.VM)
                .parentId(HostId.of("other-pve"))
                .label("其他虚拟机")
                .build();

        assertThat(targetHost.isSibling(otherParentHost)).isFalse();
    }

    @Test
    void shouldRequireIdAndTypeAndLabel() {
        assertThatThrownBy(() -> Host.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Host ID is required");

        assertThatThrownBy(() -> Host.builder()
                .id(HostId.of("test"))
                .type(HostType.VM)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Host label is required");
    }

    @Test
    void shouldHaveImmutableCollections() {
        Set<Capability> capabilities = targetHost.getCapabilities();
        assertThatThrownBy(() -> capabilities.add(Capability.VM))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
