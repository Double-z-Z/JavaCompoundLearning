package com.redis.lock.lock;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 看门狗 — 后台续期调度器
 *
 * 与具体锁实现解耦：通过函数式接口接收续期逻辑。
 * lock 成功后调用 start()，unlock 时调用 stop()。
 * 续期间隔 = TTL / 3（最小 1 秒）。
 */
class Watchdog {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "watchdog");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /**
     * 启动续期任务。
     * @param key 锁 key
     * @param ttlSeconds 锁 TTL
     * @param isLocked 检查锁是否仍被持有
     * @param renew 执行续期
     */
    void start(String key, long ttlSeconds, java.util.function.BooleanSupplier isLocked,
               java.util.function.BiFunction<String, Long, Boolean> renew) {
        long intervalSeconds = Math.max(1, ttlSeconds / 3);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (!isLocked.getAsBoolean()) {
                stop(key);
                return;
            }
            boolean renewed = renew.apply(key, ttlSeconds);
            if (!renewed) {
                stop(key);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        tasks.put(key, task);
    }

    void stop(String key) {
        ScheduledFuture<?> task = tasks.remove(key);
        if (task != null) {
            task.cancel(false);
        }
    }

    void shutdown() {
        tasks.values().forEach(t -> t.cancel(false));
        tasks.clear();
        scheduler.shutdown();
    }
}
