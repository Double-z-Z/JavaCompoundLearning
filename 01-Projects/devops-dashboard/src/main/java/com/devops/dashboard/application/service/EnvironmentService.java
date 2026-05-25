package com.devops.dashboard.application.service;

import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.exception.environment.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 环境管理核心服务接口。
 *
 * <p>负责环境的完整生命周期管理，包括创建、销毁、服务部署、状态查询等操作。
 * 作为应用层（Application Layer）的编排入口，封装领域对象的业务规则和事务边界。</p>
 *
 * <h3>V2 Phase 2 扩展</h3>
 * <p>新增与 {@link com.devops.dashboard.domain.host.Host} 聚合根的集成能力：</p>
 * <ul>
 *   <li>{@link #createFromSpec(String, EnvironmentSpec)} — 创建时自动校验目标主机角色与能力</li>
 *   <li>{@link #getAccessEndpoints(EnvironmentId)} — 获取环境访问端点</li>
 *   <li>{@link #listAll()} — 列出所有环境（MCP 查询用）</li>
 * </ul>
 *
 * @see EnvironmentServiceImpl
 */
public interface EnvironmentService {

    /**
     * 从规格说明创建环境。
     *
     * <p>V2 Phase 2 扩展：当 spec 中指定了 {@code hostId} 时，会自动校验：
     * <ol>
     *   <li>主机是否存在（{@code hosts.yml} 中已注册）</li>
     *   <li>主机是否具备 {@code TARGET} 角色</li>
     *   <li>主机是否支持指定的运行时类型（如 DOCKER 能力）</li>
     * </ol></p>
     *
     * @param name 环境名称（用户指定），为空时自动生成
     * @param spec 环境规格（包含类型、主机ID、运行时等配置）
     * @return 创建的环境实例（含 ID、状态等信息）
     * @throws EnvironmentCreationException       创建失败时抛出
     * @throws com.devops.dashboard.domain.exception.host.HostNotFoundException           主机不存在时抛出
     * @throws com.devops.dashboard.domain.exception.host.InvalidHostRoleException      主机角色不满足要求时抛出
     * @throws com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException 主机能力不匹配时抛出
     */
    Mono<Environment> createFromSpec(String name, EnvironmentSpec spec);

    /**
     * 销毁指定环境。
     *
     * <p>会停止所有服务并释放资源，将状态标记为 DESTROYED。</p>
     *
     * @param envId 环境ID
     * @throws EnvironmentNotFoundException 环境不存在时抛出
     */
    Mono<Void> destroy(EnvironmentId envId);

    /**
     * 向已有环境部署新服务。
     *
     * @param envId    目标环境ID
     * @param manifest 服务清单（引用模板+覆盖配置）
     * @return 部署的服务实例
     */
    Mono<ServiceInstance> deployService(EnvironmentId envId, ServiceManifest manifest);

    /**
     * 停止环境中的指定服务。
     *
     * @param envId      环境ID
     * @param instanceId 服务实例ID
     */
    Mono<Void> stopService(EnvironmentId envId, String instanceId);

    /**
     * 重启环境中的指定服务。
     *
     * @param envId      环境ID
     * @param instanceId 服务实例ID
     * @return 重启后的服务实例
     */
    Mono<ServiceInstance> restartService(EnvironmentId envId, String instanceId);

    /**
     * 查询环境当前状态。
     *
     * @param envId 环境ID
     * @return 当前状态枚举值
     */
    Mono<EnvironmentStatus> getStatus(EnvironmentId envId);

    /**
     * 列出环境中所有服务实例。
     *
     * @param envId 环境ID
     * @return 服务实例流
     */
    Flux<ServiceInstance> listServices(EnvironmentId envId);

    /**
     * 根据ID查找环境。
     *
     * @param envId 环境ID
     * @return 环境实例
     */
    Mono<Environment> findById(EnvironmentId envId);

    /**
     * 根据状态筛选环境列表。
     *
     * @param status 状态过滤条件，为 null 时返回全部
     * @return 符合条件的环境流
     */
    Flux<Environment> findByStatus(EnvironmentStatus status);

    /**
     * 获取环境的访问端点映射（V2 Phase 2 新增）。
     *
     * <p>返回环境中各服务的访问地址，如 Console URL、API 地址等，
     * 用于 MCP Tool 响应用户"怎么访问"的询问。</p>
     *
     * @param envId 环境ID
     * @return 访问端点名称→URL 的映射，如 {"console": "http://10.0.0.103:8848/nacos"}
     */
    Mono<Map<String, String>> getAccessEndpoints(EnvironmentId envId);

    /**
     * 按多个状态筛选环境列表（V3 新增）。
     *
     * @param statuses 状态列表
     * @return 符合条件的环境流
     */
    Flux<Environment> findByStatusIn(List<EnvironmentStatus> statuses);

    /**
     * 列出所有环境（V2 Phase 2 新增）。
     *
     * <p>供 MCP Tool 使用，返回环境的摘要信息列表，不含完整服务实例详情。</p>
     *
     * @return 所有环境的环境流
     */
    Flux<Environment> listAll();
}
