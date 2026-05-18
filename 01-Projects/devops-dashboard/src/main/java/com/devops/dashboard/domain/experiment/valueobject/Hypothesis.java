package com.devops.dashboard.domain.experiment.valueobject;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.List;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hypothesis {
    
    private String statement;  // 假设陈述
    private String background;  // 背景说明
    private List<SuccessCriterion> successCriteria;
    
    @Embeddable
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessCriterion {
        private String metric;
        private String operator;  // >= | <= | == | !=
        private Number value;
    }
}
