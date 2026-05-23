package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.host.dto.HostTopology;
import com.devops.dashboard.domain.host.*;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网络诊断 MCP Handler（Phase 3）。
 *
 * <p>提供网络路径分析功能，帮助 AI 在执行压测前评估网络拓扑对结果可信度的影响。
 * 这是 V2 设计中"网络感知"能力的核心入口。</p>
 *
 * <h3>Tool 清单</h3>
 * <table border="1">
 *   <tr><th>Tool 名称</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>{@code analyze_network_path}</td><td>{@link #analyzeNetworkPath(String, String, Integer)}</td><td>分析源→目标网络路径</td></tr>
 * </table>
 *
 * @see HostService 主机拓扑服务
 */
@Component
public class DiagnosisHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisHandler.class);

    private final HostService hostService;

    public DiagnosisHandler(McpExceptionTranslator errorTranslator, HostService hostService) {
        super(errorTranslator);
        this.hostService = hostService;
    }

    /**
     * 分析源主机到目标端口的网络路径（MCP Tool: {@code analyze_network_path}）。
     *
     * <p>根据 hosts.yml 中定义的主机拓扑，推断源和目标之间的网络路径类型，
     * 并给出路径可信度评级和建议。</p>
     *
     * <h3>使用场景</h3>
     * <pre>
     * 用户: "对 Nacos 执行压测"
     * AI: 调用 analyze_network_path(source="vm-loadgen-01", target="vm-ubuntu-test", port=8848)
     * → 返回 {pathType: "same-lan", credibility: "high", rttEstimate: "0.1-1ms"}
     * AI: "路径分析：vm-loadgen-01 与 vm-ubuntu-test 在同一局域网，RTT 约 0.1-1ms，数据可信度高。是否开始压测？"
     * </pre>
     *
     * @param sourceHostId 源主机 ID（通常是压测机）
     * @param targetHostId 目标主机 ID（被测服务所在机器）
     * @param targetPort   目标服务端口
     * @return JSON 格式的路径分析结果
     */
    public String analyzeNetworkPath(String sourceHostId, String targetHostId, Integer targetPort) {
        log.info("MCP Tool [analyze_network_path]: {} -> {}:{}",
                sourceHostId, targetHostId, targetPort);

        HostTopology topology = hostService.getTopology();

        Map<String, Object> sourceInfo = findHostInfo(topology, sourceHostId);
        Map<String, Object> targetInfo = findHostInfo(topology, targetHostId);

        NetworkPathType pathType = determinePathType(sourceHostId, targetHostId, topology);

        return toJson(Map.of(
                "source", Map.of("hostId", sourceHostId, "info", sourceInfo),
                "target", Map.of("hostId", targetHostId, "port", targetPort, "info", targetInfo),
                "pathAnalysis", buildPathAnalysis(pathType, sourceHostId, targetHostId),
                "_note", "Path analysis is based on static topology configuration. Actual RTT may vary."
        ));
    }

    private Map<String, Object> findHostInfo(HostTopology topology, String hostId) {
        return topology.getHosts().stream()
                .filter(h -> h.id().equals(hostId))
                .findFirst()
                .map(h -> Map.<String, Object>of(
                        "label", h.label(),
                        "type", h.type(),
                        "networkZone", h.networkZone(),
                        "roles", h.roles()))
                .orElseGet(() -> Map.of("error", "Host not found: " + hostId));
    }

    private NetworkPathType determinePathType(String sourceId, String targetId, HostTopology topology) {
        if (sourceId.equals(targetId)) {
            return NetworkPathType.SAME_HOST;
        }

        var sourceOpt = topology.getHosts().stream()
                .filter(h -> h.id().equals(sourceId)).findFirst();
        var targetOpt = topology.getHosts().stream()
                .filter(h -> h.id().equals(targetId)).findFirst();

        if (sourceOpt.isEmpty() || targetOpt.isEmpty()) {
            return NetworkPathType.WAN;
        }

        String sourceParent = sourceOpt.get().parentId();
        String targetParent = targetOpt.get().parentId();

        if (sourceParent != null && sourceParent.equals(targetParent)) {
            return NetworkPathType.SAME_HYPERVISOR;
        }

        String sourceZone = sourceOpt.get().networkZone();
        String targetZone = targetOpt.get().networkZone();

        if (sourceZone != null && sourceZone.equals(targetZone)) {
            return NetworkPathType.SAME_LAN;
        }

        return NetworkPathType.WAN;
    }

    private Map<String, Object> buildPathAnalysis(NetworkPathType pathType, String sourceId, String targetId) {
        double rttEstimate = switch (pathType) {
            case SAME_HOST -> 0.01;
            case SAME_HYPERVISOR -> 0.5;
            case SAME_LAN -> 1.0;
            case WAN -> 30.0;
        };

        String recommendation = switch (pathType) {
            case SAME_HOST -> "同机测试，延迟极低但无法模拟真实网络。建议在独立负载机上运行以获取更真实的结果。";
            case SAME_HYPERVISOR -> "同一虚拟化宿主机的 VM 间通信，延迟较低且稳定。适合大多数压测场景。";
            case SAME_LAN -> "同一局域网内通信，延迟低且可预测。压测结果具有较高参考价值。";
            case WAN -> "跨广域网通信，延迟高且不稳定。压测结果可能受网络波动影响，建议多次取平均值。";
        };

        return Map.of(
                "pathType", pathType.getCode(),
                "displayName", pathType.getDisplayName(),
                "credibility", pathType.getCredibility(),
                "estimatedRttMs", rttEstimate,
                "rttRange", rttEstimate < 1 ? "< 1ms" : rttEstimate < 5 ? "1-5ms" : rttEstimate < 20 ? "5-20ms" : "> 20ms",
                "recommendation", recommendation,
                "isReliable", pathType == NetworkPathType.SAME_LAN || pathType == NetworkPathType.SAME_HYPERVISOR
        );
    }
}
