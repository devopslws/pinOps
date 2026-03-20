package com.finops.domain.cost.repository;

import com.finops.domain.cost.entity.ComputeMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComputeMetricRepository extends JpaRepository<ComputeMetric, Long> {

    // 테넌트의 가장 최근 수집 스냅샷만 조회 (enriched API용)
    @Query("""
        SELECT c FROM ComputeMetric c
        WHERE c.tenantId = :tenantId
          AND c.collectedAt = (
              SELECT MAX(c2.collectedAt)
              FROM ComputeMetric c2
              WHERE c2.tenantId = :tenantId
          )
        ORDER BY c.resourceType, c.resourceId
    """)
    List<ComputeMetric> findLatestByTenantId(@Param("tenantId") Long tenantId);

    // 수집 이력 전체 (시계열 차트용)
    List<ComputeMetric> findByTenantIdOrderByCollectedAtDesc(Long tenantId);
}
