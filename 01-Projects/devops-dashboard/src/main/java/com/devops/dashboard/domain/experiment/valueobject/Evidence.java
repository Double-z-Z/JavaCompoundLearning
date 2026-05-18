package com.devops.dashboard.domain.experiment.valueobject;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {
    
    private LocalDateTime collectedAt;
    
    private List<Metric> metrics;
    
    private List<Artifact> artifacts;
    
    @Embeddable
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metric {
        private String name;           // throughput_qps
        private Number value;         // 52000
        private String unit;          // ops/sec
        private String measurementTool;  // rabbitmq-perf-test
    }
    
    @Embeddable
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact {
        private String type;          // log | graph | raw_data
        private String path;          // logs/rabbitmq-perf-test.log
    }
}
