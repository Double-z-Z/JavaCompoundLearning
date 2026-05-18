package com.devops.dashboard.domain.experiment.valueobject;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.List;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conclusion {
    
    private ExperimentDecision decision;
    private String summary;              // 结论摘要
    private List<String> lessonsLearned;  // 经验教训
    private List<String> nextSteps;      // 后续行动
    
    public static Conclusion accept(String summary, List<String> lessons, List<String> nextSteps) {
        return Conclusion.builder()
            .decision(ExperimentDecision.ACCEPT)
            .summary(summary)
            .lessonsLearned(lessons)
            .nextSteps(nextSteps)
            .build();
    }
    
    public static Conclusion reject(String summary, List<String> lessons) {
        return Conclusion.builder()
            .decision(ExperimentDecision.REJECT)
            .summary(summary)
            .lessonsLearned(lessons)
            .build();
    }
    
    public static Conclusion needMoreData(String reason) {
        return Conclusion.builder()
            .decision(ExperimentDecision.NEED_MORE_DATA)
            .summary(reason)
            .build();
    }
}
