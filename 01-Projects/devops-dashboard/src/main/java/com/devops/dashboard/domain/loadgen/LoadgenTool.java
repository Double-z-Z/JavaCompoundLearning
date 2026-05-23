package com.devops.dashboard.domain.loadgen;

import java.util.Objects;

/**
 * 压测工具枚举，定义系统支持的负载生成工具类型。
 *
 * <p>每种工具对应不同的压测场景和性能特征：
 * <ul>
 *   <li>{@link #WRK} - 适用于高并发 HTTP 压测场景</li>
 *   <li>{@link #HEY} - 适用于轻量级快速压测</li>
 *   <li>{@link #AB} - 适用于基础基准测试</li>
 * </ul>
 */
public enum LoadgenTool {

    /**
     * wrk - 现代多线程 HTTP 压测工具
     *
     * <p>适用场景：高并发、长连接、低延迟的 HTTP/HTTPS 服务压测。
     * 使用时机：需要精确控制并发数和请求速率时优先选择。
     */
    WRK("wrk", "现代多线程 HTTP 压测工具"),

    /**
     * hey - Go 语言 HTTP 负载生成器
     *
     * <p>适用场景：快速验证服务基本可用性、CI/CD 流水线中的冒烟测试。
     * 使用时机：需要单二进制文件部署、跨平台运行时选择。
     */
    HEY("hey", "Go 语言 HTTP 负载生成器"),

    /**
     * ab - Apache Bench 经典工具
     *
     * <p>适用场景：基础的吞吐量测试、与其他工具结果对比基线。
     * 使用时机：环境受限只能使用 Apache 工具链时，或需要与历史数据对比时。
     */
    AB("ab", "Apache Bench 经典工具");

    /**
     * 压测工具的命令行命令名
     *
     * <p>用于在目标主机上执行对应的压测命令，也是 {@link #fromCommand(String)} 的匹配键。
     */
    private final String command;

    /**
     * 压测工具的人类可读描述
     *
     * <p>用于 UI 展示和日志输出，帮助运维人员快速识别工具类型。
     */
    private final String description;

    /**
     * 构造压测工具枚举值
     *
     * @param command     命令行命令名，不能为 null
     * @param description 人类可读描述，不能为 null
     */
    LoadgenTool(String command, String description) {
        this.command = Objects.requireNonNull(command, "command must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    /**
     * 获取压测工具的命令行命令名
     *
     * @return 命令名，如 "wrk"、"hey"、"ab"
     */
    public String getCommand() {
        return command;
    }

    /**
     * 获取压测工具的描述信息
     *
     * @return 人类可读的工具描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据命令名查找对应的压测工具枚举值
     *
     * <p>匹配时忽略大小写，适用于从用户输入或配置文件中解析工具类型。
     *
     * @param command 命令名，如 "wrk"、"WRK"、"WrK"
     * @return 对应的压测工具枚举值
     * @throws IllegalArgumentException 如果命令名不匹配任何已知工具
     */
    public static LoadgenTool fromCommand(String command) {
        for (LoadgenTool tool : values()) {
            if (tool.command.equalsIgnoreCase(command)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("Unknown LoadgenTool: " + command);
    }
}
