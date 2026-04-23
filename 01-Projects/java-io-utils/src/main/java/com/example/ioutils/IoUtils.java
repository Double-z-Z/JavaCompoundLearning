package com.example.ioutils;

import java.io.Closeable;
import java.io.IOException;

/**
 * IO工具类 - 提供资源关闭的最佳实践封装
 *
 * <p>核心设计原则：</p>
 * <ul>
 *   <li>区分业务关闭和资源清理场景</li>
 *   <li>提供多种关闭策略：安静关闭、日志记录、异常抛出</li>
 *   <li>支持批量关闭和链式关闭</li>
 *   <li>零依赖，只使用JDK</li>
 * </ul>
 *
 * @author Your Name
 * @version 1.0
 */
public final class IoUtils {

    private IoUtils() {
        // 工具类，禁止实例化
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 安静关闭资源，忽略所有异常
     *
     * <p>适用场景：资源清理，关闭失败不影响主流程</p>
     * <ul>
     *   <li>程序退出时的清理</li>
     *   <li>异常处理中的finally块</li>
     *   <li>请求处理完成后的资源释放</li>
     * </ul>
     *
     * @param closeable 待关闭的资源，可以为null
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 资源清理场景，忽略异常
            }
        }
    }

    /**
     * 批量安静关闭多个资源
     *
     * <p>按参数顺序关闭，任何一个关闭失败都不会影响其他资源的关闭</p>
     *
     * @param closeables 待关闭的资源数组
     */
    public static void closeQuietly(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                closeQuietly(closeable);
            }
        }
    }

    /**
     * 关闭资源并记录异常到标准错误
     *
     * <p>适用场景：需要排查关闭问题，但不想中断流程</p>
     *
     * @param closeable 待关闭的资源，可以为null
     */
    public static void closeWithLogging(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                System.err.println("关闭资源失败: " + closeable.getClass().getName());
                e.printStackTrace();
            }
        }
    }

    /**
     * 关闭资源，失败时抛出异常
     *
     * <p>适用场景：业务逻辑中，关闭失败需要处理</p>
     * <ul>
     *   <li>事务提交前的资源关闭</li>
     *   <li>需要确保数据已持久化</li>
     * </ul>
     *
     * @param closeable 待关闭的资源，可以为null
     * @throws IOException 关闭失败时抛出
     */
    public static void closeAndThrow(Closeable closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * 关闭资源，失败时抛出运行时异常
     *
     * <p>适用于不希望声明checked exception的场景</p>
     *
     * @param closeable 待关闭的资源，可以为null
     * @throws RuntimeException 包装后的IOException
     */
    public static void closeAndThrowUnchecked(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                throw new RuntimeException("关闭资源失败: " + closeable.getClass().getName(), e);
            }
        }
    }
}
