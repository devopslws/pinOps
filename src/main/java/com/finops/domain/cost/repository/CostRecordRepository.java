package com.finops.domain.cost.repository;

import com.finops.domain.cost.entity.CostRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CostRecordRepository extends JpaRepository<CostRecord, Long> {

    List<CostRecord> findByTenantIdAndBillingPeriodStartBetween(
            Long tenantId, LocalDate from, LocalDate to);

    // 서비스별 비용 집계 (대시보드용)
    @Query("""
        SELECT c.serviceName, SUM(c.billedCost)
        FROM CostRecord c
        WHERE c.tenantId = :tenantId
          AND c.billingPeriodStart BETWEEN :from AND :to
        GROUP BY c.serviceName
        ORDER BY SUM(c.billedCost) DESC
    """)
    List<Object[]> sumByService(@Param("tenantId") Long tenantId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    // 월별 총 비용 - FORMATDATETIME은 H2 네이티브 함수, GROUP BY 기준도 동일 표현식 사용
    @Query(value = """
        SELECT FORMATDATETIME(billing_period_start, 'yyyy-MM'),
               SUM(billed_cost)
        FROM cost_records
        WHERE tenant_id = :tenantId
        GROUP BY FORMATDATETIME(billing_period_start, 'yyyy-MM')
        ORDER BY 1
    """, nativeQuery = true)
    List<Object[]> sumByMonth(@Param("tenantId") Long tenantId);
}
