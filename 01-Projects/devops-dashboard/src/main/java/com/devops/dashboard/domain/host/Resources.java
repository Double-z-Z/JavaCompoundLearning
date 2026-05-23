package com.devops.dashboard.domain.host;

/**
 * 资源容量值对象（Value Object）。
 *
 * <p>封装主机的 CPU 和内存资源总量与空闲量快照，用于容量规划、调度决策和健康度评估。
 * 提供利用率计算和资源容纳能力判断等派生方法。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>不可变性</b>：{@code final} 类 + {@code final} 字段 + {@link Builder} 模式，
 *       创建后状态不可变更</li>
 *   <li><b>自验证</b>：构建时校验资源总量不允许为负数</li>
 *   <li><b>值语义</b>：代表某一时刻的资源快照，不维护生命周期</li>
 * </ul>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"Host 资源信息 → cpu / memory"</em> 字段定义，
 * 数据来源为监控采集或手动配置。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Resources resources = Resources.builder()
 *     .cpuTotal(8)
 *     .cpuFree(4)
 *     .memTotalMb(16384)
 *     .memFreeMb(8192)
 *     .build();
 *
 * double cpuUsage = resources.cpuUtilizationPercent(); // 50.0
 * boolean fits = resources.canAccommodate(2, 4096);    // true
 * }</pre>
 */
public final class Resources {

    /** CPU 总核数。 */
    private final int cpuTotal;

    /** CPU 空闲核数。 */
    private final int cpuFree;

    /** 内存总容量（MB）。 */
    private final int memTotalMb;

    /** 内存空闲容量（MB）。 */
    private final int memFreeMb;

    /**
     * 私有构造器，仅由 {@link Builder#build()} 调用。
     *
     * @param builder 已完成字段填充的构建器
     */
    public Resources(Builder builder) {
        this.cpuTotal = builder.cpuTotal;
        this.cpuFree = builder.cpuFree;
        this.memTotalMb = builder.memTotalMb;
        this.memFreeMb = builder.memFreeMb;
    }

    /**
     * 获取 CPU 总核数。
     *
     * @return CPU 核数总量
     */
    public int getCpuTotal() {
        return cpuTotal;
    }

    /**
     * 获取 CPU 空闲核数。
     *
     * @return 可用的 CPU 核数
     */
    public int getCpuFree() {
        return cpuFree;
    }

    /**
     * 获取内存总容量。
     *
     * @return 内存总容量（MB）
     */
    public int getMemTotalMb() {
        return memTotalMb;
    }

    /**
     * 获取内存空闲容量。
     *
     * @return 可用内存量（MB）
     */
    public int getMemFreeMb() {
        return memFreeMb;
    }

    /**
     * 计算 CPU 利用率百分比。
     *
     * <p>公式：{@code (cpuTotal - cpuFree) / cpuTotal × 100}。
     * 当 {@code cpuTotal} 为 0 时返回 0.0 以避免除零异常。</p>
     *
     * @return CPU 利用率（0.0 ~ 100.0）
     */
    public double cpuUtilizationPercent() {
        if (cpuTotal == 0) return 0.0;
        return ((double) (cpuTotal - cpuFree) / cpuTotal) * 100;
    }

    /**
     * 计算内存利用率百分比。
     *
     * <p>公式：{@code (memTotalMb - memFreeMb) / memTotalMb × 100}。
     * 当 {@code memTotalMb} 为 0 时返回 0.0 以避免除零异常。</p>
     *
     * @return 内存利用率（0.0 ~ 100.0）
     */
    public double memUtilizationPercent() {
        if (memTotalMb == 0) return 0.0;
        return ((double) (memTotalMb - memFreeMb) / memTotalMb) * 100;
    }

    /**
     * 判断当前资源是否足以容纳指定需求。
     *
     * <p>同时检查 CPU 和内存两个维度的剩余容量是否满足最低要求，
     * 任一维度不足即返回 {@code false}。</p>
     *
     * @param requiredCpu      需要的 CPU 核数
     * @param requiredMemoryMb 需要的内存容量（MB）
     * @return 若 CPU 和内存均满足需求则返回 {@code true}
     */
    public boolean canAccommodate(int requiredCpu, int requiredMemoryMb) {
        return cpuFree >= requiredCpu && memFreeMb >= requiredMemoryMb;
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
     * {@code Resources} 的流式构建器。
     *
     * <p>CPU 和内存总量不允许为负数，其余字段默认为 0。</p>
     */
    public static class Builder {

        /** CPU 总核数，默认 0。 */
        private int cpuTotal;

        /** CPU 空闲核数，默认 0。 */
        private int cpuFree;

        /** 内存总容量（MB），默认 0。 */
        private int memTotalMb;

        /** 内存空闲容量（MB），默认 0。 */
        private int memFreeMb;

        /**
         * 设置 CPU 总核数。
         *
         * @param cpuTotal 核数（必须 ≥ 0）
         * @return 当前构建器，支持链式调用
         */
        public Builder cpuTotal(int cpuTotal) {
            this.cpuTotal = cpuTotal;
            return this;
        }

        /**
         * 设置 CPU 空闲核数。
         *
         * @param cpuFree 空闲核数
         * @return 当前构建器，支持链式调用
         */
        public Builder cpuFree(int cpuFree) {
            this.cpuFree = cpuFree;
            return this;
        }

        /**
         * 设置内存总容量。
         *
         * @param memTotalMb 容量（MB）（必须 ≥ 0）
         * @return 当前构建器，支持链式调用
         */
        public Builder memTotalMb(int memTotalMb) {
            this.memTotalMb = memTotalMb;
            return this;
        }

        /**
         * 设置内存空闲容量。
         *
         * @param memFreeMb 空闲容量（MB）
         * @return 当前构建器，支持链式调用
         */
        public Builder memFreeMb(int memFreeMb) {
            this.memFreeMb = memFreeMb;
            return this;
        }

        /**
         * 构建 {@code Resources} 实例。
         *
         * <p>校验 CPU 和内存总量不为负数后创建不可变实例。</p>
         *
         * @return 构建完成的 {@code Resources} 值对象
         * @throws IllegalArgumentException 当资源总量为负数时抛出
         */
        public Resources build() {
            if (cpuTotal < 0) {
                throw new IllegalArgumentException("CPU total cannot be negative");
            }
            if (memTotalMb < 0) {
                throw new IllegalArgumentException("Memory total cannot be negative");
            }
            return new Resources(this);
        }
    }
}
