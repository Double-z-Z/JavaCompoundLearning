package com.example.counter.controller;

import com.example.counter.config.SentinelConfig;
import com.example.counter.dto.*;
import com.example.counter.service.MultiSkuOrderService;
import com.example.counter.service.SpikeOrderMQService;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀控制器
 * 集成 Sentinel 限流 + MQ 异步下单
 *
 * 流程：
 * 1. Sentinel 限流检查（Filter 级别）
 * 2. 预扣库存（Lua 原子扣减）
 * 3. 写入 MQ（异步创建订单）
 * 4. 返回"排队中"
 */
@RestController
@RequestMapping("/spike")
public class SpikeController {

    private static final Logger log = LoggerFactory.getLogger(SpikeController.class);

    private final MultiSkuOrderService orderService;
    private final SpikeOrderMQService mqService;

    public SpikeController(MultiSkuOrderService orderService, SpikeOrderMQService mqService) {
        this.orderService = orderService;
        this.mqService = mqService;
    }

    /**
     * 秒杀下单接口
     *
     * @param requestMono 请求体（包含 SKU 和数量）
     * @param userId 用户ID（header 传递）
     * @param requestId 请求ID（header 传递，用于链路追踪）
     * @return 预扣结果（成功=排队中，失败=库存不足/限流）
     */
    @PostMapping("/order")
    public Mono<ResponseEntity<PreDeductResult>> placeSpikeOrder(
            @RequestBody Mono<MultiSkuOrderRequest> requestMono,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        // 生成请求ID（确保不变）
        final String finalRequestId = (requestId != null) ? requestId : java.util.UUID.randomUUID().toString();

        return requestMono
                .flatMap(request -> {
                    // 调用库存预扣服务
                    return orderService.placeOrder(request)
                            .map(decrementResult -> {
                                if (decrementResult.isSuccess()) {
                                    // 预扣成功，生成订单号
                                    String orderId = mqService.generateOrderId();

                                    // 构建预扣结果
                                    PreDeductResult preResult = PreDeductResult.success(
                                            orderId,
                                            decrementResult.getDecremented()
                                    );

                                    // 发送 MQ 消息（异步创建订单）
                                    final String capturedOrderId = orderId;
                                    mqService.sendOrderMessage(preResult, userId, finalRequestId)
                                            .subscribe(
                                                    null,
                                                    e -> log.error("MQ发送失败: orderId={}", capturedOrderId, e)
                                            );

                                    return ResponseEntity
                                            .status(HttpStatus.ACCEPTED)  // 202 Accepted
                                            .body(preResult);
                                } else {
                                    // 预扣失败（库存不足）
                                    return ResponseEntity
                                            .status(HttpStatus.OK)  // 200 但业务失败
                                            .body(PreDeductResult.insufficient(
                                                    decrementResult.getMessage(),
                                                    decrementResult.getDecremented(),
                                                    decrementResult.getFailed()
                                            ));
                                }
                            });
                })
                .onErrorResume(BlockException.class, e -> {
                    log.warn("Sentinel 限流触发: requestId={}", finalRequestId);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(PreDeductResult.rateLimited()));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("秒杀下单异常: requestId={}", finalRequestId, e);
                    PreDeductResult errorResult = new PreDeductResult();
                    errorResult.setSuccess(false);
                    errorResult.setMessage("系统异常，请稍后重试");
                    errorResult.setCode(500);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResult));
                });
    }

    /**
     * 限流后的友好提示
     */
    @GetMapping("/limit")
    public Mono<ResponseEntity<Map<String, Object>>> getLimitInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 429);
        result.put("success", false);
        result.put("message", "系统繁忙，请稍后重试");
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result));
    }
}