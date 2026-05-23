package com.devops.dashboard.domain.host;

/**
 * SSH 访问信息值对象（Value Object）。
 *
 * <p>封装通过 SSH 协议连接主机所需的全部认证与网络参数。
 * 作为 {@link Host} 聚合根的访问属性，提供远程执行命令、部署应用等操作的基础。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>不可变性</b>：所有字段 {@code final}，创建后不可修改，
 *       通过 {@link Builder} 模式构建实例</li>
 *   <li><b>自验证</b>：{@code sshHost} 和 {@code user} 为必填字段，
 *       构建时校验非空</li>
 *   <li><b>安全边界</b>：仅存储密钥路径（{@code keyPath}），
 *       不持有密钥内容本身，避免敏感数据泄漏到领域层</li>
 * </ul>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"SSH 访问配置"</em> 章节，
 * 映射 YAML 配置中的 {@code access.ssh_host / ssh_port / user / key_path}</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * HostAccess access = HostAccess.builder()
 *     .sshHost("192.168.1.100")
 *     .sshPort(22)
 *     .user("deploy")
 *     .keyPath("/home/deploy/.ssh/id_ed25519")
 *     .build();
 * }</pre>
 */
public class HostAccess {

    /** SSH 连接目标主机地址（IP 或域名）。 */
    private final String sshHost;

    /** SSH 服务端口，默认值为标准端口 22。 */
    private final int sshPort;

    /** SSH 登录用户名。 */
    private final String user;

    /** SSH 私钥文件路径（绝对路径或相对于运行环境的路径）。 */
    private final String keyPath;

    /**
     * 私有构造器，仅由 {@link Builder#build()} 调用。
     *
     * @param builder 已完成字段填充的构建器
     */
    private HostAccess(Builder builder) {
        this.sshHost = builder.sshHost;
        this.sshPort = builder.sshPort;
        this.user = builder.user;
        this.keyPath = builder.keyPath;
    }

    /**
     * 获取 SSH 目标主机地址。
     *
     * @return 主机 IP 或域名
     */
    public String getSshHost() {
        return sshHost;
    }

    /**
     * 获取 SSH 端口号。
     *
     * @return 端口号，通常为 22
     */
    public int getSshPort() {
        return sshPort;
    }

    /**
     * 获取 SSH 登录用户名。
     *
     * @return 用户名字符串
     */
    public String getUser() {
        return user;
    }

    /**
     * 获取 SSH 私钥文件路径。
     *
     * @return 密钥文件绝对/相对路径，可能为 {@code null}
     */
    public String getKeyPath() {
        return keyPath;
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
     * {@code HostAccess} 的流式构建器。
     *
     * <p>强制要求设置 {@code sshHost} 和 {@code user} 两个必填字段，
     * 其余字段均有合理默认值（如 {@code sshPort} 默认为 22）。</p>
     */
    public static class Builder {

        /** SSH 目标主机地址（必填）。 */
        private String sshHost;

        /** SSH 端口，默认值 22。 */
        private int sshPort = 22;

        /** 登录用户名（必填）。 */
        private String user;

        /** 私钥文件路径（可选）。 */
        private String keyPath;

        /**
         * 设置 SSH 目标主机地址。
         *
         * @param sshHost IP 地址或域名
         * @return 当前构建器，支持链式调用
         */
        public Builder sshHost(String sshHost) {
            this.sshHost = sshHost;
            return this;
        }

        /**
         * 设置 SSH 端口号。
         *
         * @param sshPort 端口号，默认 22
         * @return 当前构建器，支持链式调用
         */
        public Builder sshPort(int sshPort) {
            this.sshPort = sshPort;
            return this;
        }

        /**
         * 设置 SSH 登录用户名。
         *
         * @param user 用户名
         * @return 当前构建器，支持链式调用
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * 设置 SSH 私钥文件路径。
         *
         * @param keyPath 私钥文件的绝对或相对路径
         * @return 当前构建器，支持链式调用
         */
        public Builder keyPath(String keyPath) {
            this.keyPath = keyPath;
            return this;
        }

        /**
         * 构建 {@code HostAccess} 实例。
         *
         * <p>校验必填字段 {@code sshHost} 和 {@code user} 非空后创建不可变实例。</p>
         *
         * @return 构建完成的 {@code HostAccess} 值对象
         * @throws IllegalArgumentException 当必填字段缺失时抛出
         */
        public HostAccess build() {
            if (sshHost == null || sshHost.isBlank()) {
                throw new IllegalArgumentException("SSH host cannot be blank");
            }
            if (user == null || user.isBlank()) {
                throw new IllegalArgumentException("User cannot be blank");
            }
            return new HostAccess(this);
        }
    }
}
