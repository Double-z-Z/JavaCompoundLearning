package com.example.counter.dto;

import java.util.List;

/**
 * 批量扣减响应
 */
public class BatchDecrementResponse {

    private String sku;
    private List<DecrementResult> results;
    private BatchSummary summary;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public List<DecrementResult> getResults() {
        return results;
    }

    public void setResults(List<DecrementResult> results) {
        this.results = results;
    }

    public BatchSummary getSummary() {
        return summary;
    }

    public void setSummary(BatchSummary summary) {
        this.summary = summary;
    }

    public static class BatchSummary {
        private int total;
        private int success;
        private int failed;
        private Long finalStock;

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getSuccess() {
            return success;
        }

        public void setSuccess(int success) {
            this.success = success;
        }

        public int getFailed() {
            return failed;
        }

        public void setFailed(int failed) {
            this.failed = failed;
        }

        public Long getFinalStock() {
            return finalStock;
        }

        public void setFinalStock(Long finalStock) {
            this.finalStock = finalStock;
        }
    }
}
