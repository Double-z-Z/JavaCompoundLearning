package com.devops.dashboard.infrastructure.environment;

import com.devops.dashboard.domain.environment.ServiceInstance;
import com.devops.dashboard.domain.environment.ServiceInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceInstanceRepository extends JpaRepository<ServiceInstance, String> {

    Optional<ServiceInstance> findByInstanceId(String instanceId);

    List<ServiceInstance> findByServiceTemplate(String template);

    List<ServiceInstance> findByEnvironment_Id(String environmentId);

    List<ServiceInstance> findByStatus(ServiceInstanceStatus status);
}
