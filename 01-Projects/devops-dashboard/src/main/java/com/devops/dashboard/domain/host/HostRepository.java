package com.devops.dashboard.domain.host;

import java.util.List;
import java.util.Optional;

/**
 * 主机仓储接口（Repository Interface）。
 *
 * <p>定义对 {@link Host} 聚合根的持久化和查询操作契约。
 * 作为 DDD 中防腐层的核心组件，将领域模型与具体存储实现解耦。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口隔离</b>：仅暴露当前领域所需的查询方法，避免泛化的 CRUD 接口</li>
 *   <li><b>返回领域对象</b>：所有方法直接返回 {@link Host} 聚合根，
 *       不暴露底层持久化模型</li>
 *   <li><b>Optional 语义</b>：单对象查询使用 {@link Optional} 表达"可能不存在"</li>
 *   <li><b>集合不可变性</b>：列表查询返回的集合由调用方决定是否防护</li>
 * </ul>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"数据访问层 → HostRepository"</em> 定义，
 * 实现类负责从 YAML 配置文件或数据库加载主机拓扑数据。</p>
 *
 * <h3>实现要求</h3>
 * <ul>
 *   <li>实现类位于 {@code infrastructure.persistence} 包</li>
 *   <li>建议注册为 Spring {@code @Component} 以支持依赖注入</li>
 *   <li>查询方法应线程安全（支持并发只读访问）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * HostRepository repo = context.getBean(HostRepository.class);
 *
 * // 按 ID 查找
 * Optional<Host> host = repo.findById(HostId.of("prod-web-01"));
 *
 * // 按角色筛选
 * List<Host> targets = repo.findByRole(HostRole.TARGET);
 *
 * // 查找子节点
 * List<Host> children = repo.findChildren(HostId.of("pve-node-1"));
 * }</pre>
 */
public interface HostRepository {

    /**
     * 根据主机 ID 查找唯一的聚合根实例。
     *
     * @param id 主机标识符值对象
     * @return 包含匹配主机的 {@link Optional}，不存在时为 {@link Optional#empty()}
     */
    Optional<Host> findById(HostId id);

    /**
     * 查询全部主机聚合根。
     *
     * <p>适用于拓扑全景展示、全局遍历等场景。
     * 返回顺序由具体实现决定（通常按配置文件中的声明顺序）。</p>
     *
     * @return 所有主机的列表，永不为 {@code null}（空库返回空列表）
     */
    List<Host> findAll();

    /**
     * 根据角色筛选主机列表。
     *
     * <p>一台主机可承担多个角色，此处返回<strong>包含</strong>指定角色的所有主机。
     * 例如查询 {@link HostRole#TARGET} 将返回所有可作为部署目标的节点。</p>
     *
     * @param role 目标角色枚举
     * @return 具有该角色的主机列表，永不为 {@code null}
     */
    List<Host> findByRole(HostRole role);

    /**
     * 根据主机类型筛选主机列表。
     *
     * <p>常用于按部署形态分组展示，如列出所有虚拟机或所有 hypervisor。</p>
     *
     * @param type 目标类型枚举
     * @return 匹配该类型的主机列表，永不为 {@code null}
     */
    List<Host> findByType(HostType type);

    /**
     * 查找指定父节点的全部子节点。
     *
     * <p>用于构建拓扑树结构，配合 {@link Host#getParentId()} 和
     * {@link Host#isSibling(Host)} 完成层次关系的遍历与渲染。</p>
     *
     * @param parentId 父节点主机 ID
     * @return 该父节点下的直接子节点列表，永不为 {@code null}
     */
    List<Host> findChildren(HostId parentId);

    /**
     * 判断指定 ID 的主机是否存在。
     *
     * <p>相比 {@link #findById(HostId)} 更轻量，避免在仅需判断存在性时加载完整聚合根。
     * 适用于前置校验、去重检查等场景。</p>
     *
     * @param id 主机标识符值对象
     * @return 若仓储中存在该 ID 的主机则返回 {@code true}
     */
    boolean existsById(HostId id);
}
