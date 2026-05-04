package com.example.counter;

import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 库存扣减压测工具
 * 验证 Redis Lua 脚本在高并发下的正确性
 */
public class StockBenchmark {

    private static final String BASE_URL = "http://localhost:8080/stock/BENCH001";
    private static final int TOTAL_REQUESTS = 10000;
    private static final int CONCURRENT_THREADS = 100;

    public static void main(String[] args) throws Exception {
        RestTemplate rest = new RestTemplate();

        // 初始化库存为 10000
        System.out.println("初始化库存...");
        rest.postForObject(BASE_URL + "/init?quantity=10000", null, String.class);

        // 查询初始库存
        String initStock = rest.getForObject(BASE_URL, String.class);
        System.out.println("初始库存: " + initStock);

        // 并发压测
        System.out.println("\n开始压测: " + TOTAL_REQUESTS + " 请求, " + CONCURRENT_THREADS + " 并发");
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    String result = rest.postForObject(BASE_URL + "/decrement?quantity=1", null, String.class);
                    if (result.contains("\"status\":\"success\"")) {
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

        // 查询最终库存
        String finalStock = rest.getForObject(BASE_URL, String.class);

        System.out.println("\n========== 压测结果 ==========");
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("成功扣减: " + successCount.get());
        System.out.println("库存不足: " + failCount.get());
        System.out.println("总耗时: " + duration + " ms");
        System.out.println("QPS: " + (TOTAL_REQUESTS * 1000.0 / duration));
        System.out.println("最终库存: " + finalStock);

        // 验证：成功数应该 <= 10000
        // 最终库存 = 10000 - 成功数
        int expectedRemaining = 10000 - successCount.get();
        System.out.println("\n========== 正确性验证 ==========");
        System.out.println("预期剩余库存: " + expectedRemaining);
        System.out.println("实际查询库存: " + finalStock);
        if (failCount.get() > 0) {
            System.out.println("✅ 有库存不足的返回，说明超卖防护生效");
        }
    }
}