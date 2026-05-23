package com.devops.dashboard.domain.host;

import java.util.Objects;

/**
 * 主机标识符值对象（Value Object）。
 *
 * <p>封装主机唯一标识，作为 {@link Host} 聚合根的身份标识。
 * 遵循 DDD 值对象规范：不可变、基于值的相等性（{@code equals/hashCode}）。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>不可变性</b>：{@code final} 类 + {@code private} 构造器 + {@code final} 字段，
 *       创建后状态不可变更</li>
 *   <li><b>自验证</b>：构造时校验 {@code value} 非空且非空白</li>
 *   <li><b>值语义</b>：两个 {@code HostId} 在内部字符串相等时视为同一对象，
 *       不依赖对象引用身份</li>
 * </ul>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"Host 基础信息 → id"</em> 字段定义。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * HostId id = HostId.of("prod-web-01");
 * String raw = id.value(); // "prod-web-01"
 * }</pre>
 */
public final class HostId {

    /** 主机标识原始字符串值，创建后不可变。 */
    private final String value;

    /**
     * 私有构造器，强制通过工厂方法 {@link #of(String)} 创建实例。
     *
     * @param value 标识符字符串，不允许为 {@code null} 或空白
     * @throws IllegalArgumentException 当 {@code value} 为空或空白时抛出
     */
    private HostId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Host ID cannot be blank");
        }
        this.value = value.trim();
    }

    /**
     * 工厂方法，创建 {@code HostId} 实例。
     *
     * <p>自动去除首尾空白字符后封装为值对象。</p>
     *
     * @param value 主机标识字符串
     * @return 封装后的 {@code HostId} 实例，永不为 {@code null}
     * @throws IllegalArgumentException 当 {@code value} 为 {@code null} 或空白时抛出
     */
    public static HostId of(String value) {
        return new HostId(value);
    }

    /**
     * 返回封装的原始标识字符串。
     *
     * @return 主机标识字符串（已 trim）
     */
    public String value() {
        return value;
    }

    /**
     * 基于值的相等性判断。
     *
     * <p>两个 {@code HostId} 当其内部 {@code value} 字符串相等时即视为相同，
     * 符合值对象契约。</p>
     *
     * @param o 比较目标对象
     * @return 若 {@code o} 为 {@code HostId} 且内部值相等则返回 {@code true}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HostId hostId)) return false;
        return Objects.equals(value, hostId.value);
    }

    /**
     * 基于值的哈希码计算，与 {@link #equals(Object)} 保持一致。
     *
     * @return 基于 {@code value} 的哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * 返回可读字符串表示，格式为 {@code "HostId{<value>}"}。
     *
     * @return 包含标识值的字符串表示
     */
    @Override
    public String toString() {
        return "HostId{" + value + "}";
    }
}
