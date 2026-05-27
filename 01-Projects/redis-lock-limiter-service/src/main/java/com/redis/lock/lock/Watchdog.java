package com.redis.lock.lock;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 看门狗 — 后台续期调度器
 *
 * 职责：lock 成功后启动定时续期，unlock 或锁丢失时停止。
 * 续期间隔 = TTL / 3，保证在 GC 停顿不超过 2/3 TTL 时锁不丢失。
 */
class Watchdog {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "watchdog");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    void start(String key, long ttlSeconds, RedisLock owner) {
        long intervalSeconds = Math.max(1, ttlSeconds / 3);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (!owner.isLocked(key)) {
                stop(key);
                return;
            }
            boolean renewed = owner.renew(key, ttlSeconds);
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
