package com.devops.dashboard.domain.loadgen;

/**
 * 压测任务状态枚举。
 *
 * <p>定义压测任务从创建到完成的完整生命周期状态：</p>
 * <ul>
 *   <li>{@link #PENDING} — 已提交，等待执行</li>
 *   <li>{@link #RUNNING} — 正在执行中</li>
 *   <li>{@link #COMPLETED} — 执行完成，结果已收集</li>
 *   <li>{@link #FAILED} — 执行失败（命令错误、超时、连接中断等）</li>
 *   <li>{@link #CANCELLED} — 用户主动取消</li>
 * </ul>
 *
 * @see com.devops.dashboard.domain.loadgen.LoadTestResult 压测结果
 */
public enum LoadTestStatus {

    PENDING("pending", "等待执行"),
    RUNNING("running", "执行中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "执行失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String displayName;

    LoadTestStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
}
