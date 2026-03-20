package com.finops.domain.cost.entity;

import com.finops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * FOCUS (FinOps Open Cost & Usage Specification) 표준 기반 비용 레코드.
 * https://focus.finops.org/
 */
@Entity
@Table(name = "cost_records",
       indexes = {
           @Index(name = "idx_cost_tenant_period", columnList = "tenant_id, billing_period_start"),
           @Index(name = "idx_cost_service", columnList = "tenant_id, service_name")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class CostRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 멀티테넌트 키 ---
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // --- FOCUS 표준 필드 ---
    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;         // ex. AmazonEC2

    @Column(name = "service_category", length = 50)
    private String serviceCategory;     // ex. Compute

    @Column(name = "region_name", length = 50)
    private String regionName;          // ex. ap-northeast-2

    @Column(name = "billed_cost", nullable = false, precision = 15, scale = 6)
    private BigDecimal billedCost;

    @Column(name = "usage_quantity", precision = 15, scale = 6)
    private BigDecimal usageQuantity;

    @Column(name = "usage_unit", length = 50)
    private String usageUnit;           // ex. Hrs, GB-Mo

    @Column(name = "billing_currency", length = 10)
    @Builder.Default
    private String billingCurrency = "USD";

    @Column(name = "resource_id", length = 200)
    private String resourceId;          // ex. i-0abc123

    @Column(name = "charge_type", length = 50)
    private String chargeType;          // ex. Usage, Tax, Credit

    // --- 원본 참조 ---
    @Column(name = "provider", length = 20)
    @Builder.Default
    private String provider = "AWS";
}
