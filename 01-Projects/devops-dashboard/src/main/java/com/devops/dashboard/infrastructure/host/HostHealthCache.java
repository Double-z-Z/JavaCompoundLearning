package com.devops.dashboard.infrastructure.host;

import com.devops.dashboard.domain.host.HostHealthStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主机健康状态缓存。
 *
 * <p>由 {@code HostHealthChecker} 定期写入，供 {@code env_list} 等查询端读取。</p>
 */
@Component
public class HostHealthCache {

    private final ConcurrentHashMap<String, HostHealthStatus> cache = new ConcurrentHashMap<>();

    public void update(String hostId, HostHealthStatus status) {
        cache.put(hostId, status);
    }

    public HostHealthStatus get(String hostId) {
        return cache.getOrDefault(hostId, HostHealthStatus.UNKNOWN);
    }

    public Map<String, HostHealthStatus> snapshot() {
        return Map.copyOf(cache);
    }

    public int size() {
        return cache.size();
    }
}
