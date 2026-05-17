package com.redis.lock;

import com.redis.lock.config.RedisConfig;
import com.redis.lock.limiter.FixedWindowLimiter;
import com.redis.lock.limiter.SlidingWindowLimiter;
import com.redis.lock.limiter.TokenBucketLimiter;
import com.redis.lock.lock.DistributedLock;
import com.redis.lock.lock.LuaScripts;
import com.redis.lock.lock.RedisLock;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁与限流器性能基准测试
 *
 * 运行方式：mvn exec:java -Dexec.mainClass="com.redis.lock.LockLimiterBenchmark" -Dexec.classpathScope=test
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class LockLimiterBenchmark {

    private DistributedLock distributedLock;
    private SlidingWindowLimiter slidingWindowLimiter;
    private FixedWindowLimiter fixedWindowLimiter;
    private TokenBucketLimiter tokenBucketLimiter;

    @Setup
    public void setup() {
        RedisConfig config = new RedisConfig();
        var redisTemplate = config.stringRedisTemplate();
        var luaScripts = new LuaScripts();
        luaScripts.init();

        distributedLock = new RedisLock(redisTemplate, luaScripts);
        slidingWindowLimiter = new SlidingWindowLimiter(redisTemplate);
        fixedWindowLimiter = new FixedWindowLimiter(redisTemplate);
        tokenBucketLimiter = new TokenBucketLimiter(redisTemplate);
    }

    @Benchmark
    public void lockUnlock() {
        String key = "bench:lock:" + System.nanoTime();
        distributedLock.lock(key, 30);
        distributedLock.unlock(key);
    }

    @Benchmark
    public void slidingWindowLimiter() {
        String key = "bench:sliding:" + System.nanoTime();
        slidingWindowLimiter.allow(key, 1000, 60);
    }

    @Benchmark
    public void fixedWindowLimiter() {
        String key = "bench:fixed:" + System.nanoTime();
        fixedWindowLimiter.allowWithTimeSlice(key, 1000, 60);
    }

    @Benchmark
    public void tokenBucketLimiter() {
        String key = "bench:token:" + System.nanoTime();
        tokenBucketLimiter.allowWithParams(key, 1000, 10);
    }

    public static void main(String[] args) throws Exception {
        Options options = new OptionsBuilder()
            .include(LockLimiterBenchmark.class.getSimpleName())
            .build();
        new Runner(options).run();
    }
}