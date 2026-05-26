package com.devops.dashboard.infrastructure.host;

import com.devops.dashboard.domain.host.Capability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时能力缓存。
 *
 * <p>存储通过 {@code host_install_docker} 等 Tool 在运行时新增的能力，
 * 作为静态 {@code hosts.yml} 的补充。通过 JSON 文件持久化，进程重启后自动恢复。</p>
 */
@Component
public class RuntimeCapabilityStore {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCapabilityStore.class);

    private final ConcurrentHashMap<String, Set<Capability>> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storagePath;

    public RuntimeCapabilityStore(
            @Value("${devops.runtime-capabilities.path:data/runtime-capabilities.json}") String storagePath) {
        this.storagePath = Paths.get(storagePath);
    }

    @PostConstruct
    void init() {
        loadFromFile();
    }

    public void add(String hostId, Capability capability) {
        cache.merge(hostId, Set.of(capability), (existing, incoming) -> {
            var merged = new HashSet<>(existing);
            merged.addAll(incoming);
            return Collections.unmodifiableSet(merged);
        });
        saveToFile();
    }

    public Set<Capability> get(String hostId) {
        return cache.getOrDefault(hostId, Set.of());
    }

    public boolean hasCapability(String hostId, Capability capability) {
        return cache.getOrDefault(hostId, Set.of()).contains(capability);
    }

    public void remove(String hostId, Capability capability) {
        cache.computeIfPresent(hostId, (k, existing) -> {
            var updated = new HashSet<>(existing);
            updated.remove(capability);
            return updated.isEmpty() ? null : Collections.unmodifiableSet(updated);
        });
        saveToFile();
    }

    public Map<String, Set<Capability>> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(cache));
    }

    private void loadFromFile() {
        if (!Files.exists(storagePath)) {
            log.info("No runtime capabilities file at {}, starting fresh", storagePath);
            return;
        }
        try {
            Map<String, Set<String>> raw = objectMapper.readValue(
                    storagePath.toFile(),
                    new TypeReference<Map<String, Set<String>>>() {});
            raw.forEach((hostId, codes) -> {
                Set<Capability> capabilities = new HashSet<>();
                for (String code : codes) {
                    try {
                        capabilities.add(Capability.valueOf(code.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown capability '{}' for host '{}' in {}, skipping", code, hostId, storagePath);
                    }
                }
                if (!capabilities.isEmpty()) {
                    cache.put(hostId, Collections.unmodifiableSet(capabilities));
                }
            });
            log.info("Loaded runtime capabilities from {}: {} hosts restored", storagePath, cache.size());
        } catch (IOException e) {
            log.warn("Failed to load runtime capabilities from {}: {}", storagePath, e.getMessage());
        }
    }

    private void saveToFile() {
        Map<String, Set<String>> serializable = new HashMap<>();
        cache.forEach((hostId, caps) -> {
            Set<String> codes = new HashSet<>();
            for (Capability c : caps) {
                codes.add(c.getCode());
            }
            serializable.put(hostId, codes);
        });
        try {
            Files.createDirectories(storagePath.getParent());
            objectMapper.writeValue(storagePath.toFile(), serializable);
            log.debug("Runtime capabilities saved to {}", storagePath);
        } catch (IOException e) {
            log.error("Failed to persist runtime capabilities to {}: {}", storagePath, e.getMessage());
        }
    }
}
