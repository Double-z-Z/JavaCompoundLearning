package com.devops.dashboard.domain.experiment.valueobject;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hypothesis {

    private String statement;
    private String background;

    // JSON 存储成功标准列表（TEXT 列兼容 H2/PG）
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private List<SuccessCriterion> successCriteria = new ArrayList<>();

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessCriterion {
        private String metric;
        private String operator;
        private Number value;
    }
}
