package com.devops.dashboard.interfaces.dto;

import com.devops.dashboard.domain.experiment.*;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "实验响应")
public class ExperimentResponse {

    @Schema(description = "实验ID", example = "exp-abc12345")
    private String id;

    @Schema(description = "实验标题", example = "Nacos注册中心性能测试")
    private String title;

    @Schema(description = "创建者", example = "developer")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "实验状态", example = "RUNNING")
    private ExperimentStatus status;

    @Schema(description = "假设说明")
    private String hypothesisStatement;

    @Schema(description = "假设背景")
    private String hypothesisBackground;

    @Schema(description = "成功标准")
    private String successCriteria;

    @Schema(description = "环境ID（关联的实验环境）")
    private String environmentId;

    @Schema(description = "证据收集时间")
    private LocalDateTime evidenceCollectedAt;

    @Schema(description = "证据指标")
    private String evidenceMetrics;

    @Schema(description = "证据附件")
    private String evidenceArtifacts;

    @Schema(description = "结论决策")
    private String conclusionDecision;

    @Schema(description = "结论摘要")
    private String conclusionSummary;

    @Schema(description = "经验教训")
    private String conclusionLessons;

    @Schema(description = "后续步骤")
    private String conclusionNextSteps;

    @Schema(description = "归档时间")
    private LocalDateTime archivedAt;

    @Schema(description = "归档路径")
    private String archivePath;

    public static ExperimentResponse fromEntity(Experiment experiment) {
        ExperimentResponseBuilder builder = ExperimentResponse.builder()
                .id(experiment.getId().getValue())
                .title(experiment.getTitle())
                .createdBy(experiment.getCreatedBy())
                .createdAt(experiment.getCreatedAt())
                .status(experiment.getStatus())
                .environmentId(experiment.getEnvironmentId());

        if (experiment.getHypothesis() != null) {
            builder.hypothesisStatement(experiment.getHypothesis().getStatement())
                    .hypothesisBackground(experiment.getHypothesis().getBackground())
                    .successCriteria(formatSuccessCriteria(experiment.getHypothesis().getSuccessCriteria()));
        }

        if (experiment.getEvidence() != null) {
            builder.evidenceCollectedAt(experiment.getEvidence().getCollectedAt())
                    .evidenceMetrics(formatMetrics(experiment.getEvidence().getMetrics()))
                    .evidenceArtifacts(formatArtifacts(experiment.getEvidence().getArtifacts()));
        }

        if (experiment.getConclusion() != null) {
            builder.conclusionDecision(experiment.getConclusion().getDecision().name())
                    .conclusionSummary(experiment.getConclusion().getSummary())
                    .conclusionLessons(formatList(experiment.getConclusion().getLessonsLearned()))
                    .conclusionNextSteps(formatList(experiment.getConclusion().getNextSteps()));
        }

        builder.archivedAt(experiment.getArchivedAt())
                .archivePath(experiment.getArchivePath());

        return builder.build();
    }

    private static String formatSuccessCriteria(List<Hypothesis.SuccessCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < criteria.size(); i++) {
            Hypothesis.SuccessCriterion sc = criteria.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"metric\":\"").append(sc.getMetric()).append("\",\"operator\":\"").append(sc.getOperator()).append("\",\"value\":").append(sc.getValue()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatMetrics(List<Evidence.Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < metrics.size(); i++) {
            Evidence.Metric m = metrics.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(m.getName()).append("\",\"value\":").append(m.getValue()).append(",\"unit\":\"").append(m.getUnit()).append("\",\"measurementTool\":\"").append(m.getMeasurementTool()).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatArtifacts(List<Evidence.Artifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < artifacts.size(); i++) {
            Evidence.Artifact a = artifacts.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"").append(a.getType()).append("\",\"path\":\"").append(a.getPath()).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return list.toString();
    }
}