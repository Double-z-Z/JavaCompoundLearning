/**
 * 共享基础设施异常
 *
 * 技术故障异常，表达"技术调用失败了"：
 * - 命令执行失败
 * - 资源配额超限
 * - 服务部署失败
 */
package com.devops.dashboard.domain.exception.shared;

public abstract class SharedException extends RuntimeException {

    protected SharedException(String message) {
        super(message);
    }

    protected SharedException(String message, Throwable cause) {
        super(message, cause);
    }
}