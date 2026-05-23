package com.devops.dashboard.domain.host;

/**
 * 网络路径值对象（Value Object）。
 *
 * <p>封装两台主机之间网络通信路径的分析结果，包含路径类型、预估跳数、RTT、
 * 是否涉及 NAT/桥接/物理网卡等拓扑特征，以及针对压测场景的警告和建议。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>不可变性</b>：所有字段 {@code final}，通过 {@link Builder} 创建后不可修改</li>
 *   <li><b>自验证</b>：构建时强制要求 {@code pathType} 非空</li>
 *   <li><b>分析产物</b>：本对象为 {@link NetworkPathAnalyzer} 的输出，
 *       不自行执行网络探测逻辑</li>
 * </ul>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"网络路径分析"</em> 章节，
 * 是压测编排阶段的关键输入——根据路径类型决定是否允许发起压测任务。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * NetworkPath path = NetworkPath.builder()
 *     .pathType(NetworkPathType.SAME_LAN)
 *     .estimatedHops(2)
 *     .estimatedRttMs(0.5)
 *     .natTraversal(false)
 *     .goesThroughBridge(true)
 *     .physicalNicInvolved(true)
 *     .warning(null)
 *     .recommendation("推荐此拓扑进行压测")
 *     .build();
 * }</pre>
 */
public class NetworkPath {

    /** 网络路径类型分类，决定结果可信度等级。 */
    private final NetworkPathType pathType;

    /** 预估网络跳数（经过的路由设备数量）。 */
    private final int estimatedHops;

    /** 预估往返延迟（毫秒）。 */
    private final double estimatedRttMs;

    /** 是否涉及 NAT 地址转换。 */
    private final boolean natTraversal;

    /** 是否经过 Linux Bridge 或虚拟交换机。 */
    private final boolean goesThroughBridge;

    /** 是否涉及物理网卡（非纯虚拟网络）。 */
    private final boolean physicalNicInvolved;

    /** 针对此路径的警告信息（可为空表示无警告）。 */
    private final String warning;

    /** 针对压测场景的建议（可为空）。 */
    private final String recommendation;

    /**
     * 私有构造器，仅由 {@link Builder#build()} 调用。
     *
     * @param builder 已完成字段填充的构建器
     */
    public NetworkPath(Builder builder) {
        this.pathType = builder.pathType;
        this.estimatedHops = builder.estimatedHops;
        this.estimatedRttMs = builder.estimatedRttMs;
        this.natTraversal = builder.natTraversal;
        this.goesThroughBridge = builder.goesThroughBridge;
        this.physicalNicInvolved = builder.physicalNicInvolved;
        this.warning = builder.warning;
        this.recommendation = builder.recommendation;
    }

    /**
     * 获取网络路径类型。
     *
     * @return 路径类型枚举，永不为 {@code null}
     */
    public NetworkPathType getPathType() {
        return pathType;
    }

    /**
     * 获取预估网络跳数。
     *
     * @return 跳数（≥ 0）
     */
    public int getEstimatedHops() {
        return estimatedHops;
    }

    /**
     * 获取预估往返延迟。
     *
     * @return RTT（毫秒）
     */
    public double getEstimatedRttMs() {
        return estimatedRttMs;
    }

    /**
     * 判断是否涉及 NAT 地址转换。
     *
     * @return 若路径中存在 NAT 设备则返回 {@code true}
     */
    public boolean isNatTraversal() {
        return natTraversal;
    }

    /**
     * 判断是否经过桥接设备。
     *
     * @return 若流量经过 Linux Bridge/OVS 则返回 {@code true}
     */
    public boolean isGoesThroughBridge() {
        return goesThroughBridge;
    }

    /**
     * 判断是否涉及物理网卡。
     *
     * <p>物理网卡参与意味着更真实的网络开销，
     * 对应 {@link NetworkPathType#SAME_LAN} 及以上级别。</p>
     *
     * @return 若流量经过物理 NIC 则返回 {@code true}
     */
    public boolean isPhysicalNicInvolved() {
        return physicalNicInvolved;
    }

    /**
     * 获取路径警告信息。
     *
     * @return 警告文本，若无警告则返回 {@code null}
     */
    public String getWarning() {
        return warning;
    }

    /**
     * 获取压测建议。
     *
     * @return 建议文本，若无可建议则返回 {@code null}
     */
    public String getRecommendation() {
        return recommendation;
    }

    /**
     * 创建新的 {@link Builder} 实例。
     *
     * @return 空状态的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code NetworkPath} 的流式构建器。
     *
     * <p>仅 {@code pathType} 为必填字段，其余均为可选并具有合理默认值（false / 0 / null）。</p>
     */
    public static class Builder {

        /** 网络路径类型（必填）。 */
        private NetworkPathType pathType;

        /** 预估跳数，默认 0。 */
        private int estimatedHops;

        /** 预估 RTT（ms），默认 0.0。 */
        private double estimatedRttMs;

        /** 是否 NAT，默认 false。 */
        private boolean natTraversal;

        /** 是否过桥接，默认 false。 */
        private boolean goesThroughBridge;

        /** 是否涉及物理网卡，默认 false。 */
        private boolean physicalNicInvolved;

        /** 警告信息，可选。 */
        private String warning;

        /** 建议，可选。 */
        private String recommendation;

        /**
         * 设置网络路径类型。
         *
         * @param pathType 路径类型（必填）
         * @return 当前构建器，支持链式调用
         */
        public Builder pathType(NetworkPathType pathType) {
            this.pathType = pathType;
            return this;
        }

        /**
         * 设置预估跳数。
         *
         * @param estimatedHops 路由跳数
         * @return 当前构建器，支持链式调用
         */
        public Builder estimatedHops(int estimatedHops) {
            this.estimatedHops = estimatedHops;
            return this;
        }

        /**
         * 设置预估往返延迟。
         *
         * @param estimatedRttMs RTT（毫秒）
         * @return 当前构建器，支持链式调用
         */
        public Builder estimatedRttMs(double estimatedRttMs) {
            this.estimatedRttMs = estimatedRttMs;
            return this;
        }

        /**
         * 设置是否涉及 NAT。
         *
         * @param natTraversal 是否存在 NAT
         * @return 当前构建器，支持链式调用
         */
        public Builder natTraversal(boolean natTraversal) {
            this.natTraversal = natTraversal;
            return this;
        }

        /**
         * 设置是否经过桥接设备。
         *
         * @param goesThroughBridge 是否经过 Bridge
         * @return 当前构建器，支持链式调用
         */
        public Builder goesThroughBridge(boolean goesThroughBridge) {
            this.goesThroughBridge = goesThroughBridge;
            return this;
        }

        /**
         * 设置是否涉及物理网卡。
         *
         * @param physicalNicInvolved 是否涉及物理 NIC
         * @return 当前构建器，支持链式调用
         */
        public Builder physicalNicInvolved(boolean physicalNicInvolved) {
            this.physicalNicInvolved = physicalNicInvolved;
            return this;
        }

        /**
         * 设置警告信息。
         *
         * @param warning 警告文本
         * @return 当前构建器，支持链式调用
         */
        public Builder warning(String warning) {
            this.warning = warning;
            return this;
        }

        /**
         * 设置建议文本。
         *
         * @param recommendation 建议内容
         * @return 当前构建器，支持链式调用
         */
        public Builder recommendation(String recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        /**
         * 构建 {@code NetworkPath} 实例。
         *
         * <p>校验 {@code pathType} 必填后创建不可变实例。</p>
         *
         * @return 构建完成的 {@code NetworkPath} 值对象
         * @throws IllegalArgumentException 当 {@code pathType} 未设置时抛出
         */
        public NetworkPath build() {
            if (pathType == null) {
                throw new IllegalArgumentException("Network path type is required");
            }
            return new NetworkPath(this);
        }
    }
}
