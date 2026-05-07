package com.example.counter.dto;

import java.util.List;

/**
 * 批量扣减请求
 */
public class BatchDecrementRequest {

    private List<DecrementItem> requests;

    public List<DecrementItem> getRequests() {
        return requests;
    }

    public void setRequests(List<DecrementItem> requests) {
        this.requests = requests;
    }

    public static class DecrementItem {
        private String requestId;
        private long quantity;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public long getQuantity() {
            return quantity;
        }

        public void setQuantity(long quantity) {
            this.quantity = quantity;
        }
    }
}
