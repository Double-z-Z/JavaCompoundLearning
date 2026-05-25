package com.devops.dashboard.application.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务模板配置加载器。
 *
 * <p>从 {@code service-templates.yml} 加载已注册服务的模板定义，
 * 提供按服务名查询健康检查端口和路径的能力。</p>
 */
@Component
public class ServiceTemplatesConfig {

    private static final Logger log = LoggerFactory.getLogger(ServiceTemplatesConfig.class);

    private final ConcurrentHashMap<String, TemplateEntry> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        try {
            var resource = new ClassPathResource("service-templates.yml");
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(resource.getInputStream());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) root.get("templates");
            if (list != null) {
                for (Map<String, Object> entry : list) {
                    String id = (String) entry.get("id");
                    if (id != null) {
                        Map<String, Object> hc = getMap(entry, "healthCheck");
                        int port = 8080;
                        String path = "/";
                        if (hc != null) {
                            if (hc.get("port") instanceof Number n) port = n.intValue();
                            if (hc.get("path") instanceof String s) path = s;
                        }
                        // 解析 defaultPorts 第一个端口映射
                        if (hc == null || !hc.containsKey("port")) {
                            List<String> ports = getStringList(entry, "defaultPorts");
                            if (ports != null && !ports.isEmpty()) {
                                String first = ports.get(0);
                                String[] parts = first.split(":");
                                if (parts.length == 2) port = Integer.parseInt(parts[0]);
                            }
                        }
                        templates.put(id, new TemplateEntry(id, port, path));
                        log.debug("[ServiceTemplates] Loaded template: id={}, port={}, path={}", id, port, path);
                    }
                }
            }
            log.info("[ServiceTemplates] Loaded {} service templates", templates.size());
        } catch (Exception e) {
            log.error("[ServiceTemplates] Failed to load service-templates.yml: {}", e.getMessage(), e);
        }
    }

    public Optional<TemplateEntry> get(String serviceName) {
        return Optional.ofNullable(templates.get(serviceName));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        return val instanceof Map ? (Map<String, Object>) val : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        return val instanceof List ? (List<String>) val : null;
    }

    public record TemplateEntry(String id, int port, String healthPath) {}
}
