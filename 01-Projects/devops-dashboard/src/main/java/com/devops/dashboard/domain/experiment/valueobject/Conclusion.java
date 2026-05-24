package com.devops.dashboard.domain.experiment.valueobject;

import com.devops.dashboard.domain.experiment.ExperimentDecision;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conclusion {

    private ExperimentDecision decision;
    private String summary;

    // JSON 存储字符串列表（TEXT 列兼容 H2/PG）
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private List<String> lessonsLearned = new ArrayList<>();

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private List<String> nextSteps = new ArrayList<>();

    public static Conclusion accept(String summary, List<String> lessons, List<String> nextSteps) {
        return Conclusion.builder()
            .decision(ExperimentDecision.ACCEPT)
            .summary(summary)
            .lessonsLearned(lessons != null ? lessons : new ArrayList<>())
            .nextSteps(nextSteps != null ? nextSteps : new ArrayList<>())
            .build();
    }

    public static Conclusion reject(String summary, List<String> lessons) {
        return Conclusion.builder()
            .decision(ExperimentDecision.REJECT)
            .summary(summary)
            .lessonsLearned(lessons != null ? lessons : new ArrayList<>())
            .build();
    }

    public static Conclusion needMoreData(String reason) {
        return Conclusion.builder()
            .decision(ExperimentDecision.NEED_MORE_DATA)
            .summary(reason)
            .build();
    }
}
