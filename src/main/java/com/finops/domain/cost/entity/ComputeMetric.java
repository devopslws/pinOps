package com.finops.domain.cost.entity;

import com.finops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EC2 / ECS 컴퓨팅 자원 사용률 스냅샷
 * - CloudWatch에서 수집한 CPU/메모리 메트릭을 저장
 * - 수집 시점(collectedAt) 기준으로 이력 보관
 */
@Entity
@Table(name = "compute_metrics", indexes = {
        @Index(name = "idx_cm_tenant_type", columnList = "tenant_id, resource_type"),
        @Index(name = "idx_cm_collected_at", columnList = "tenant_id, collected_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ComputeMetric extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** EC2 | ECS */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 10)
    private ResourceType resourceType;

    /** EC2: instanceId  /  ECS: clusterName */
    @Column(name = "resource_id", nullable = false, length = 200)
    private String resourceId;

    /** EC2: instanceType (t2.micro)  /  ECS: serviceName */
    @Column(name = "resource_name", length = 200)
    private String resourceName;

    /** ECS 전용: 클러스터 이름 */
    @Column(name = "cluster_name", length = 200)
    private String clusterName;

    // ── CPU ─────────────────────────────────────────
    @Column(name = "avg_cpu_percent", precision = 6, scale = 2)
    private BigDecimal avgCpuPercent;

    @Column(name = "max_cpu_percent", precision = 6, scale = 2)
    private BigDecimal maxCpuPercent;

    // ── 메모리 (ECS만 기본 제공, EC2는 CW Agent 필요) ──
    @Column(name = "avg_memory_percent", precision = 6, scale = 2)
    private BigDecimal avgMemoryPercent;

    @Column(name = "max_memory_percent", precision = 6, scale = 2)
    private BigDecimal maxMemoryPercent;

    // ── ECS 태스크 수 ────────────────────────────────
    @Column(name = "desired_count")
    private Integer desiredCount;

    @Column(name = "running_count")
    private Integer runningCount;

    /** 과소사용 여부: HIGH / MEDIUM / LOW / UNKNOWN */
    @Enumerated(EnumType.STRING)
    @Column(name = "waste_score", length = 10)
    @Builder.Default
    private WasteScore wasteScore = WasteScore.UNKNOWN;

    /** 메트릭 수집 시각 */
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    // ── Enum ─────────────────────────────────────────

    public enum ResourceType { EC2, ECS }

    public enum WasteScore {
        HIGH,    // 낭비 심각 (절감 강력 권장)
        MEDIUM,  // 낭비 의심
        LOW,     // 적정 사용
        UNKNOWN  // 데이터 없음
    }

    // ── 팩토리 메서드 ─────────────────────────────────

    public static WasteScore calcWasteScore(double avgCpu, ResourceType type) {
        double highThreshold   = type == ResourceType.ECS ? 15.0 : 10.0;
        double mediumThreshold = type == ResourceType.ECS ? 30.0 : 20.0;

        if (avgCpu == 0)            return WasteScore.UNKNOWN;
        if (avgCpu < highThreshold) return WasteScore.HIGH;
        if (avgCpu < mediumThreshold) return WasteScore.MEDIUM;
        return WasteScore.LOW;
    }
}
