package com.example.counter.dto;

/**
 * 单笔扣减结果
 */
public class DecrementResult {

    private String requestId;
    private String status;
    private long remaining;

    public DecrementResult() {
    }

    public DecrementResult(String requestId, String status, long remaining) {
        this.requestId = requestId;
        this.status = status;
        this.remaining = remaining;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getRemaining() {
        return remaining;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }
}
