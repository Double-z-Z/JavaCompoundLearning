package com.devops.dashboard.interfaces.dto;

import com.devops.dashboard.domain.experiment.ExperimentDecision;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Schema(description = "提交实验结论请求")
public class ConclusionRequest {

    @NotNull(message = "决策不能为空")
    @Schema(description = "决策结论", example = "ACCEPT")
    private String decision;

    @NotBlank(message = "结论摘要不能为空")
    @Schema(description = "结论摘要", example = "Nacos单节点可承受每秒1000次注册请求，延迟<50ms")
    private String summary;

    @Schema(description = "经验教训", example = "水平扩展是关键,需要预热阶段")
    private String lessonsLearned;

    @Schema(description = "后续步骤", example = "压测报告归档,推广到生产环境")
    private String nextSteps;

    public Conclusion toConclusion() {
        return Conclusion.builder()
                .decision(ExperimentDecision.valueOf(decision))
                .summary(summary)
                .lessonsLearned(parseList(lessonsLearned))
                .nextSteps(parseList(nextSteps))
                .build();
    }

    private List<String> parseList(String input) {
        if (input == null || input.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}