package com.example.counter.dto;

import java.util.Map;

/**
 * 秒杀预扣结果
 * 用于 MQ 异步下单前的预扣库存结果
 */
public class PreDeductResult {
    private boolean success;
    private String message;
    private String orderId;
    private Map<String, Long> decremented;  // sku -> remaining stock
    private Map<String, Integer> failed;    // sku -> failed qty
    private Integer code;

    public static PreDeductResult success(String orderId, Map<String, Long> decremented) {
        PreDeductResult result = new PreDeductResult();
        result.success = true;
        result.message = "排队中";
        result.orderId = orderId;
        result.decremented = decremented;
        result.code = 202;  // Accepted - 排队中
        return result;
    }

    public static PreDeductResult insufficient(String message, Map<String, Long> decremented, Map<String, Integer> failed) {
        PreDeductResult result = new PreDeductResult();
        result.success = false;
        result.message = message;
        result.decremented = decremented;
        result.failed = failed;
        result.code = 200;
        return result;
    }

    public static PreDeductResult rateLimited() {
        PreDeductResult result = new PreDeductResult();
        result.success = false;
        result.message = "系统繁忙，请稍后重试";
        result.code = 429;
        return result;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public Map<String, Long> getDecremented() { return decremented; }
    public void setDecremented(Map<String, Long> decremented) { this.decremented = decremented; }
    public Map<String, Integer> getFailed() { return failed; }
    public void setFailed(Map<String, Integer> failed) { this.failed = failed; }
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
}