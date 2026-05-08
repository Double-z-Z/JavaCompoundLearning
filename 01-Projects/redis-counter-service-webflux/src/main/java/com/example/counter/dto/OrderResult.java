package com.example.counter.dto;

import java.util.Map;

public class OrderResult {
    private boolean success;
    private String message;
    private Map<String, Long> decremented; // sku -> remaining stock
    private Map<String, Integer> failed;    // sku -> failed qty

    public OrderResult() {}

    public static OrderResult success(Map<String, Long> decremented) {
        OrderResult result = new OrderResult();
        result.success = true;
        result.message = "OK";
        result.decremented = decremented;
        return result;
    }

    public static OrderResult failure(String message, Map<String, Long> decremented) {
        OrderResult result = new OrderResult();
        result.success = false;
        result.message = message;
        result.decremented = decremented;
        return result;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Long> getDecremented() { return decremented; }
    public void setDecremented(Map<String, Long> decremented) { this.decremented = decremented; }
    public Map<String, Integer> getFailed() { return failed; }
    public void setFailed(Map<String, Integer> failed) { this.failed = failed; }
}