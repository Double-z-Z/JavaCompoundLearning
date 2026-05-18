package com.devops.dashboard.domain.experiment;

import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.experiment.valueobject.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "experiments", indexes = {
    @Index(name = "idx_exp_status", columnList = "status"),
    @Index(name = "idx_exp_created_by", columnList = "createdBy")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experiment {
    
    @EmbeddedId
    private ExperimentId id;
    
    private String title;
    
    private String createdBy;
    
    private LocalDateTime createdAt;
    
    @Enumerated(EnumType.STRING)
    private ExperimentStatus status;
    
    // === 值对象 ===
    @Embedded
    private Hypothesis hypothesis;
    
    @Embedded
    private Evidence evidence;
    
    @Embedded
    private Conclusion conclusion;
    
    // === 实验环境（组合关系）===
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_env_id")
    private Environment experimentEnvironment;
    
    // === 归档信息 ===
    private LocalDateTime archivedAt;
    private String archivePath;  // "docs/spikes/xxx.md"
    
    public static Experiment createSpike(SpikeRequest request) {
        var experiment = new Experiment();
        experiment.id = ExperimentId.generate();
        experiment.title = request.getTitle();
        experiment.createdBy = request.getCreatedBy();
        experiment.createdAt = LocalDateTime.now();
        experiment.status = ExperimentStatus.PLANNING;
        experiment.hypothesis = request.getHypothesis();
        
        return experiment;
    }
    
    public void start(Environment env) {
        validateTransition(ExperimentStatus.RUNNING);
        this.status = ExperimentStatus.RUNNING;
        this.experimentEnvironment = env;
    }
    
    public void conclude(Conclusion conclusion) {
        validateTransition(ExperimentStatus.COMPLETED);
        this.status = ExperimentStatus.COMPLETED;
        this.conclusion = conclusion;
    }
    
    public void archive(String archivePath) {
        if (this.status != ExperimentStatus.COMPLETED) {
            throw new IllegalStateException("Only completed experiments can be archived");
        }
        
        this.status = ExperimentStatus.ARCHIVED;
        this.archivedAt = LocalDateTime.now();
        this.archivePath = archivePath;
    }
    
    public void cancel() {
        validateTransition(ExperimentStatus.CANCELLED);
        this.status = ExperimentStatus.CANCELLED;
    }
    
    public void recordEvidence(Evidence evidence) {
        if (this.status != ExperimentStatus.RUNNING) {
            throw new IllegalStateException("Can only record evidence while running");
        }
        this.evidence = evidence;
    }
    
    public boolean isActive() {
        return status == ExperimentStatus.RUNNING || status == ExperimentStatus.PLANNING;
    }
    
    public boolean isCompleted() {
        return status == ExperimentStatus.COMPLETED || 
               status == ExperimentStatus.ARCHIVED ||
               status == ExperimentStatus.CANCELLED;
    }
    
    private void validateTransition(ExperimentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", this.status, target)
            );
        }
    }
}
