package com.devops.dashboard.domain.environment;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetNodeRef {
    private String nodeId;
    private String ip;
    private String role;
}
