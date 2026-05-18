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

    // JSON 存储成功标准列表（避免 JPA 嵌套集合限制）
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
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
