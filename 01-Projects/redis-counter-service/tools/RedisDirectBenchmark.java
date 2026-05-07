import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.ConnectionPoolConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Cluster Direct Benchmark - Large Scale
 * Compile: javac -cp jedis-5.0.0.jar;commons-pool2-2.12.0.jar;slf4j-api-1.7.36.jar RedisDirectBenchmark.java
 * Run: java -cp .;jedis-5.0.0.jar;commons-pool2-2.12.0.jar;slf4j-api-1.7.36.jar RedisDirectBenchmark
 */
public class RedisDirectBenchmark {

    private static final String STOCK_KEY = "stock:BENCH001";
    
    private static final int WARMUP_REQUESTS = 50000;   // 增加到 5 万预热
    private static final int TOTAL_REQUESTS = 500000;    // 50 万正式测试
    private static final int CONCURRENT_THREADS = 100;
    private static final int TEST_ROUNDS = 3;            // 跑 3 轮取平均

    private static final String DECREMENT_SCRIPT = 
        "local stock = tonumber(redis.call('GET', KEYS[1]) or 0) " +
        "local quantity = tonumber(ARGV[1]) " +
        "if stock >= quantity then " +
        "    return redis.call('DECRBY', KEYS[1], quantity) " +
        "else " +
        "    return -1 " +
        "end";

    public static void main(String[] args) throws Exception {
        System.out.println("========== Redis Cluster Direct Benchmark (Large Scale) ==========");
        System.out.println("Threads: " + CONCURRENT_THREADS);
        System.out.println("Total Requests per round: " + TOTAL_REQUESTS);
        System.out.println("Rounds: " + TEST_ROUNDS);
        System.out.println();

        Set<HostAndPort> nodes = new HashSet<>();
        nodes.add(new HostAndPort("10.0.0.102", 6379));
        nodes.add(new HostAndPort("10.0.0.103", 6379));
        nodes.add(new HostAndPort("10.0.0.104", 6379));
        nodes.add(new HostAndPort("10.0.0.105", 6379));
        nodes.add(new HostAndPort("10.0.0.106", 6379));
        nodes.add(new HostAndPort("10.0.0.107", 6379));

        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(200);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(10);
        
        JedisCluster jedisCluster = new JedisCluster(nodes, 3000, 3000, 3, poolConfig);

        System.out.println("Initializing stock...");
        jedisCluster.set(STOCK_KEY, "10000000");
        String initialStock = jedisCluster.get(STOCK_KEY);
        System.out.println("Initial stock: " + initialStock);
        System.out.println();

        // 大规模预热
        System.out.println("Warming up (" + WARMUP_REQUESTS + " requests)...");
        long warmupStart = System.currentTimeMillis();
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            jedisCluster.eval(DECREMENT_SCRIPT, 1, STOCK_KEY, "1");
            if (i > 0 && i % 10000 == 0) {
                System.out.println("  Warmup progress: " + i + "/" + WARMUP_REQUESTS);
            }
        }
        long warmupDuration = System.currentTimeMillis() - warmupStart;
        System.out.println("Warmup complete in " + warmupDuration + "ms, warmup QPS: " + 
            (WARMUP_REQUESTS * 1000L / warmupDuration));
        
        jedisCluster.set(STOCK_KEY, "10000000");
        System.out.println();

        // 多轮正式测试
        double[] qpsResults = new double[TEST_ROUNDS];
        double[] latencyResults = new double[TEST_ROUNDS];

        for (int round = 0; round < TEST_ROUNDS; round++) {
            System.out.println("========== Round " + (round + 1) + "/" + TEST_ROUNDS + " ==========");
            
            // 重置库存
            jedisCluster.set(STOCK_KEY, "10000000");
            
            // 强制 GC，减少干扰
            System.gc();
            Thread.sleep(1000);
            
            long startTime = System.currentTimeMillis();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            AtomicLong totalLatency = new AtomicLong(0);

            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
            CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

            for (int i = 0; i < TOTAL_REQUESTS; i++) {
                executor.submit(() -> {
                    long reqStart = System.nanoTime();
                    try {
                        Object result = jedisCluster.eval(DECREMENT_SCRIPT, 1, STOCK_KEY, "1");
                        long latency = System.nanoTime() - reqStart;
                        totalLatency.addAndGet(latency);
                        
                        if (result != null && ((Long) result) >= 0) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                    latch.countDown();
                });
            }

            latch.await();
            long duration = System.currentTimeMillis() - startTime;
            executor.shutdown();

            double qps = TOTAL_REQUESTS * 1000.0 / duration;
            double avgLatencyMs = (totalLatency.get() / (double) TOTAL_REQUESTS) / 1_000_000.0;
            
            qpsResults[round] = qps;
            latencyResults[round] = avgLatencyMs;

            System.out.printf("QPS: %.2f | Avg Latency: %.3f ms | Success: %d | Failed: %d | Duration: %dms%n",
                qps, avgLatencyMs, successCount.get(), failCount.get(), duration);
            System.out.println();
        }

        // 统计结果
        double avgQps = 0, avgLatency = 0, minQps = Double.MAX_VALUE, maxQps = 0;
        for (int i = 0; i < TEST_ROUNDS; i++) {
            avgQps += qpsResults[i];
            avgLatency += latencyResults[i];
            minQps = Math.min(minQps, qpsResults[i]);
            maxQps = Math.max(maxQps, qpsResults[i]);
        }
        avgQps /= TEST_ROUNDS;
        avgLatency /= TEST_ROUNDS;

        System.out.println("========== Final Statistics ==========");
        System.out.printf("Average QPS:        %.2f%n", avgQps);
        System.out.printf("Min QPS:            %.2f%n", minQps);
        System.out.printf("Max QPS:            %.2f%n", maxQps);
        System.out.printf("QPS Std Dev:        %.2f%n", stdDev(qpsResults, avgQps));
        System.out.printf("Average Latency:    %.3f ms%n", avgLatency);
        System.out.println();

        System.out.println("========== Bottleneck Analysis ==========");
        System.out.printf("HTTP Full Chain (JMeter):  ~33,000 QPS%n");
        System.out.printf("HTTP Full Chain (bombardier): ~38,700 QPS%n");
        System.out.printf("Redis Direct (Jedis):      ~%.0f QPS%n", avgQps);
        System.out.printf("Difference:                ~%.0f QPS (%.1f%% overhead)%n", 
            (avgQps - 38700), (1 - 38700/avgQps) * 100);
        
        if (avgQps > 60000) {
            System.out.println("Conclusion: Bottleneck is in Spring MVC / JSON layer");
        } else if (avgQps > 45000) {
            System.out.println("Conclusion: Both HTTP and Redis layers need optimization");
        } else {
            System.out.println("Conclusion: Bottleneck is in Redis client / network / Lua execution");
        }

        jedisCluster.close();
        System.out.println("\nBenchmark complete!");
    }
    
    private static double stdDev(double[] values, double mean) {
        double sum = 0;
        for (double v : values) {
            sum += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sum / values.length);
    }
}
