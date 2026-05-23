package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.host.dto.HostTopology;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 发现类 Handler：提供只读资源查询，是 MCP 客户端发现系统能力的入口。
 *
 * <h3>暴露的资源列表</h3>
 * <table border="1">
 *   <tr><th>URI</th><th>方法</th><th>返回格式</th><th>数据来源</th></tr>
 *   <tr><td>{@code /mcp/resources/hosts/topology}</td><td>GET</td><td>{@link HostTopology} JSON</td><td>{@code HostService.getTopology()}</td></tr>
 *   <tr><td>{@code /mcp/resources/templates/list}</td><td>GET</td><td>模板列表 JSON</td><td>{@code service-templates.yml}</td></tr>
 * </table>
 *
 * @see HostService
 */
@Component
public class DiscoveryHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryHandler.class);

    private final HostService hostService;

    private volatile Map<String, Object> cachedTemplates;
    private long templatesCacheTime = 0;
    private static final long TEMPLATE_CACHE_TTL_MS = 60_000;

    public DiscoveryHandler(McpExceptionTranslator errorTranslator, HostService hostService) {
        super(errorTranslator);
        this.hostService = hostService;
    }

    /**
     * 查询主机拓扑结构。
     *
     * @return 拓扑数据的 JSON 字符串
     */
    public String getHostsTopology() {
        log.info("MCP: Fetching hosts topology");
        try {
            HostTopology topology = hostService.getTopology();
            return toJson(topology);
        } catch (Exception e) {
            log.error("Failed to fetch hosts topology", e);
            return toErrorJson(errorTranslator.translate(e));
        }
    }

    /**
     * 获取可用服务模板列表（从 service-templates.yml 动态加载）。
     *
     * @return 模板列表 JSON
     */
    public String getTemplatesList() {
        log.info("MCP: Fetching templates list");
        try {
            Map<String, Object> templates = loadTemplates();
            return toJson(templates);
        } catch (Exception e) {
            log.error("Failed to load templates", e);
            return toJson(Map.of(
                    "templates", List.of(),
                    "error", "Failed to load templates: " + e.getMessage()
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplates() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedTemplates != null && (now - templatesCacheTime) < TEMPLATE_CACHE_TTL_MS) {
            return cachedTemplates;
        }

        Yaml yaml = new Yaml();
        InputStream inputStream = new ClassPathResource("service-templates.yml").getInputStream();
        try (inputStream) {
            Map<String, Object> root = yaml.load(inputStream);
            cachedTemplates = root;
            templatesCacheTime = now;
            log.debug("Templates loaded from YAML, {} templates found",
                    ((List<?>) root.get("templates")).size());
            return root;
        }
    }
}
