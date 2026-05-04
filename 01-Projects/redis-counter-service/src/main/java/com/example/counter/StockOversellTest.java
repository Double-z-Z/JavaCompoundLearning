package com.example.counter;

import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 库存超卖防护验证
 * 场景：100 库存，200 请求（每个扣 1），验证 Lua 脚本能否防止超卖
 */
public class StockOversellTest {

    private static final String BASE_URL = "http://localhost:8080/stock/OVER001";
    private static final int TOTAL_REQUESTS = 200;
    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_THREADS = 50;

    public static void main(String[] args) throws Exception {
        RestTemplate rest = new RestTemplate();

        // 初始化库存为 100
        System.out.println("初始化库存: " + INITIAL_STOCK);
        rest.postForObject(BASE_URL + "/init?quantity=" + INITIAL_STOCK, null, String.class);

        String initStock = rest.getForObject(BASE_URL, String.class);
        System.out.println("初始库存查询: " + initStock);

        // 并发压测：200 请求扣减 1，库存只有 100
        System.out.println("\n开始压测: " + TOTAL_REQUESTS + " 请求扣减, 初始库存 " + INITIAL_STOCK);
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    String result = rest.postForObject(BASE_URL + "/decrement?quantity=1", null, String.class);
                    if (result.contains("\"status\":\"success\"")) {
                        successCount.incrementAndGet();
                    } else if (result.contains("insufficient_stock")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("请求失败: " + e.getMessage());
                }
                latch.countDown();
            });
        }

        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();

        String finalStock = rest.getForObject(BASE_URL, String.class);

        System.out.println("\n========== 压测结果 ==========");
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("成功扣减: " + successCount.get());
        System.out.println("库存不足: " + failCount.get());
        System.out.println("QPS: " + (TOTAL_REQUESTS * 1000.0 / duration));

        System.out.println("\n========== 正确性验证 ==========");
        System.out.println("初始库存: " + INITIAL_STOCK);
        System.out.println("最终库存: " + finalStock);

        // 关键验证：成功数必须 <= 初始库存
        if (successCount.get() <= INITIAL_STOCK) {
            System.out.println("✅ 超卖防护生效！成功扣减数(" + successCount.get() + ") <= 初始库存(" + INITIAL_STOCK + ")");
        } else {
            System.out.println("❌ 超卖发生！成功扣减数(" + successCount.get() + ") > 初始库存(" + INITIAL_STOCK + ")");
        }

        // 最终库存验证
        int actualRemaining = INITIAL_STOCK - successCount.get();
        System.out.println("预期库存: " + actualRemaining);
    }
}