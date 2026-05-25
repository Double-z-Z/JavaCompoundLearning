package com.devops.dashboard.infrastructure.host;

import com.devops.dashboard.domain.host.Capability;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时能力缓存。
 *
 * <p>存续通过 {@code host_install_docker} 等 Tool 在运行时新增的能力，
 * 作为静态 {@code hosts.yml} 的补充。仅存续于当前进程生命周期。</p>
 */
@Component
public class RuntimeCapabilityStore {

    private final ConcurrentHashMap<String, Set<Capability>> cache = new ConcurrentHashMap<>();

    public void add(String hostId, Capability capability) {
        cache.merge(hostId, Set.of(capability), (existing, incoming) -> {
            var merged = new java.util.HashSet<>(existing);
            merged.addAll(incoming);
            return Collections.unmodifiableSet(merged);
        });
    }

    public Set<Capability> get(String hostId) {
        return cache.getOrDefault(hostId, Set.of());
    }

    public boolean hasCapability(String hostId, Capability capability) {
        return cache.getOrDefault(hostId, Set.of()).contains(capability);
    }

    public void remove(String hostId, Capability capability) {
        cache.computeIfPresent(hostId, (k, existing) -> {
            var updated = new java.util.HashSet<>(existing);
            updated.remove(capability);
            return updated.isEmpty() ? null : Collections.unmodifiableSet(updated);
        });
    }
}
