package com.devops.dashboard.domain.experiment;

public enum ExperimentDecision {
    ACCEPT("采纳方案", "假设成立，建议采用该技术/架构"),
    REJECT("拒绝方案", "假设不成立，不建议采用"),
    NEED_MORE_DATA("数据不足", "实验结果不明确，需要更多验证"),
    INCONCLUSIVE("结论矛盾", "结果存在矛盾，无法得出明确结论");

    private final String displayName;
    private final String description;

    ExperimentDecision(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}