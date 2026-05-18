package com.devops.dashboard.domain.experiment.valueobject;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {

    private LocalDateTime collectedAt;

    // JSON 存储指标数据（避免 JPA 嵌套集合限制）
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<Metric> metrics = new ArrayList<>();

    // JSON 存储制品数据
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<Artifact> artifacts = new ArrayList<>();

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metric {
        private String name;
        private Number value;
        private String unit;
        private String measurementTool;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact {
        private String type;
        private String path;
    }
}
