package com.devops.dashboard.domain.host;

/**
 * 网络路径分析器接口（Domain Service）。
 *
 * <p>封装两台主机之间网络通信路径的分析逻辑，输出 {@link NetworkPath} 值对象。
 * 属于领域服务层接口，具体实现由基础设施层提供（如基于拓扑图算法的实现）。</p>
 *
 * <h3>职责定位</h3>
 * <p>本接口不属于单一实体的行为，而是涉及多实体协作的跨聚合操作：
 * <ul>
 *   <li>根据源/目标主机的拓扑位置判断 {@link NetworkPathType}</li>
 *   <li>估算网络跳数和往返延迟</li>
 *   <li>检测 NAT、桥接、物理网卡等拓扑特征</li>
 * </ul></p>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"网络路径分析服务"</em> 章节，
 * 是压测编排引擎的前置依赖——在发起压测前必须先完成路径分析以评估结果可信度。</p>
 *
 * <h3>实现要求</h3>
 * <ul>
 *   <li>实现类应位于 {@code infrastructure} 层</li>
 *   <li>分析结果应为确定性输出（相同输入 → 相同输出）</li>
 *   <li>不依赖外部实时探测，基于静态拓扑数据计算</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * NetworkPathAnalyzer analyzer = context.getBean(NetworkPathAnalyzer.class);
 * NetworkPath path = analyzer.analyze(sourceHost, targetHost, 8080);
 * double rtt = analyzer.estimateRtt(path.getPathType());
 * }</pre>
 */
public interface NetworkPathAnalyzer {

    /**
     * 分析从源主机到目标主机（指定端口）的网络通信路径。
     *
     * <p>综合两台主机的拓扑位置、网络区域、虚拟化层级等信息，
     * 输出完整的路径分析结果，包括类型、跳数、RTT 估算和拓扑特征标记。</p>
     *
     * @param source     源主机（流量发起方），如压测执行节点
     * @param target     目标主机（流量接收方），如被测服务节点
     * @param targetPort 目标端口号，用于端口级路径分析
     * @return 封装了完整分析结果的 {@link NetworkPath} 值对象
     */
    NetworkPath analyze(Host source, Host target, int targetPort);

    /**
     * 根据路径类型估算往返延迟（RTT）。
     *
     * <p>基于经验值或历史统计数据返回预估 RTT，
     * 用于压测前的可行性预判和超时参数推荐。</p>
     *
     * @param pathType 网络路径类型
     * @return 预估 RTT（毫秒）
     */
    double estimateRtt(NetworkPathType pathType);

    /**
     * 判断指定主机间的通信路径是否经过物理网卡。
     *
     * <p>物理网卡参与是区分"真实网络测试"与"纯虚拟环境测试"的关键指标，
     * 直接影响压测结果的可信度评级。</p>
     *
     * @param source 源主机
     * @param target 目标主机
     * @return 若路径中存在物理网卡参与则返回 {@code true}
     */
    boolean isPhysicalNicInvolved(Host source, Host target);
}
