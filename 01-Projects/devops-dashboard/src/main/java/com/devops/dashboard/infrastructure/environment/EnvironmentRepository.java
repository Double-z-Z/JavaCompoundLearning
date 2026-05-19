package com.devops.dashboard.infrastructure.environment;

import com.devops.dashboard.domain.environment.Environment;
import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.environment.EnvironmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, EnvironmentId> {

    Optional<Environment> findByName(String name);

    List<Environment> findByStatus(EnvironmentStatus status);

    List<Environment> findByStatusIn(List<EnvironmentStatus> statuses);

    boolean existsByName(String name);

    long countByStatus(EnvironmentStatus status);
}
