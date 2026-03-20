package com.finops.domain.aws.repository;

import com.finops.domain.aws.entity.AwsIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AwsIntegrationRepository extends JpaRepository<AwsIntegration, Long> {
    Optional<AwsIntegration> findByTenantId(Long tenantId);
}
