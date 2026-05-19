package com.devops.dashboard.infrastructure.experiment;

import com.devops.dashboard.domain.experiment.Experiment;
import com.devops.dashboard.domain.experiment.ExperimentId;
import com.devops.dashboard.domain.experiment.ExperimentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, ExperimentId> {

    List<Experiment> findByStatus(ExperimentStatus status);

    List<Experiment> findByStatusIn(List<ExperimentStatus> statuses);

    List<Experiment> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    Optional<Experiment> findByTitleContainingIgnoreCase(String title);

    long countByStatus(ExperimentStatus status);

    boolean existsByTitle(String title);
}
