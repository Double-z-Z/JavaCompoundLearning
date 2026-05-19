package com.devops.dashboard.interfaces.dto;

import com.devops.dashboard.domain.experiment.*;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "创建Spike实验请求")
public class CreateExperimentRequest {

    @NotBlank(message = "实验标题不能为空")
    @Schema(description = "实验标题", example = "Nacos注册中心性能测试")
    private String title;

    @NotBlank(message = "创建者不能为空")
    @Schema(description = "创建者", example = "developer")
    private String createdBy;

    @NotBlank(message = "假设说明不能为空")
    @Schema(description = "假设说明", example = "Nacos单节点可承受每秒1000次注册请求")
    private String hypothesisStatement;

    @Schema(description = "假设背景", example = "当前生产环境面临高并发注册场景")
    private String hypothesisBackground;

    @Schema(description = "成功标准（JSON数组）", example = "[{\"metric\":\"tps\",\"operator\":\">=\",\"value\":\"1000\"}]")
    private String successCriteria;

    @Schema(description = "最大存活时间（分钟）", example = "120")
    private Integer maxLifetimeMinutes;
}