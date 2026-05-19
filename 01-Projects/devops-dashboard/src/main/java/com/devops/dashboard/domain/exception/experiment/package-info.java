/**
 * 实验领域异常
 *
 * 实验聚合根相关的异常类型：
 * - {@link ExperimentNotFoundException} - 实验不存在
 * - {@link ExperimentAlreadyConcludedException} - 实验已结束无法操作
 * - {@link ExperimentLifetimeExceededException} - 实验超过最大存活时间
 * - {@link InvalidExperimentTransitionException} - 状态转移无效
 */
package com.devops.dashboard.domain.exception.experiment;