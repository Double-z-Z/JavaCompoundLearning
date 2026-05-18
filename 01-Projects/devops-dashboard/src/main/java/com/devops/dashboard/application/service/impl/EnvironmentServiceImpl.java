package com.devops.dashboard.application.service.impl;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.application.service.ServiceManifest;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.exception.EnvironmentNotFoundException;
import com.devops.dashboard.infrastructure.persistence.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Transactional
public class EnvironmentServiceImpl implements EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentServiceImpl.class);

    private final EnvironmentRepository environmentRepository;

    @Override
    public Mono<Environment> createFromSpec(String name, EnvironmentSpec spec) {
        return Mono.fromCallable(() -> {
            log.info("Creating environment: name={}, type={}", name, spec.getType());

            String envName = (name != null && !name.isBlank()) ? name : generateEnvironmentName(spec.getType());
            Environment environment = Environment.create(envName, spec);

            Environment saved = environmentRepository.save(environment);
            log.info("Environment saved to database: id={}, name={}", saved.getId().getValue(), saved.getName());

            return saved;
        });
    }

    @Override
    public Mono<Void> destroy(EnvironmentId envId) {
        return Mono.fromRunnable(() -> {
            log.info("Destroying environment: {}", envId.getValue());

            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));

            environment.markAsDestroyed();
            environmentRepository.save(environment);

            log.info("Environment marked as destroyed: {}", envId.getValue());
        });
    }

    @Override
    public Mono<ServiceInstance> deployService(EnvironmentId envId, ServiceManifest manifest) {
        return Mono.fromCallable(() -> {
            log.info("Deploying service {} to environment: {}", manifest.getTemplateName(), envId.getValue());

            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));

            ServiceInstance instance = ServiceInstance.create(
                    manifest.getTemplateName(),
                    manifest.getImage()
            );

            environment.addService(instance);
            environmentRepository.save(environment);

            log.info("Service deployed: instanceId={}", instance.getInstanceId());
            return instance;
        });
    }

    @Override
    public Mono<Void> stopService(EnvironmentId envId, String instanceId) {
        return Mono.fromRunnable(() -> {
            log.info("Stopping service {} in environment: {}", instanceId, envId.getValue());

            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));

            environment.findServiceByInstanceId(instanceId)
                    .ifPresent(ServiceInstance::markAsStopped);

            environmentRepository.save(environment);
        });
    }

    @Override
    public Mono<ServiceInstance> restartService(EnvironmentId envId, String instanceId) {
        return Mono.fromCallable(() -> {
            log.info("Restarting service {} in environment: {}", instanceId, envId.getValue());

            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));

            ServiceInstance instance = environment.findServiceByInstanceId(instanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Service instance not found: " + instanceId));

            instance.markAsStopped();
            instance.markAsDeploying();

            environmentRepository.save(environment);
            return instance;
        });
    }

    @Override
    public Mono<EnvironmentStatus> getStatus(EnvironmentId envId) {
        return Mono.fromCallable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            return environment.getStatus();
        });
    }

    @Override
    public Flux<ServiceInstance> listServices(EnvironmentId envId) {
        return Flux.fromIterable(() -> {
            Environment environment = environmentRepository.findById(envId)
                    .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()));
            return environment.getServices().iterator();
        });
    }

    @Override
    public Mono<Environment> findById(EnvironmentId envId) {
        return Mono.fromCallable(() ->
                environmentRepository.findById(envId)
                        .orElseThrow(() -> new EnvironmentNotFoundException(envId.getValue()))
        );
    }

    @Override
    public Flux<Environment> findByStatus(EnvironmentStatus status) {
        if (status != null) {
            return Flux.fromIterable(environmentRepository.findByStatus(status));
        }
        return Flux.fromIterable(environmentRepository.findAll());
    }

    private String generateEnvironmentName(EnvironmentType type) {
        String prefix = switch (type) {
            case DEV -> "dev";
            case TEST -> "test";
            case STAGING -> "staging";
            case PROD -> "prod";
            case EXPERIMENT -> "exp";
        };
        return prefix + "-" + System.currentTimeMillis();
    }
}
