package com.finops.domain.cost.dto;

import com.finops.domain.cost.entity.ComputeMetric.ResourceType;
import com.finops.domain.cost.entity.ComputeMetric.WasteScore;

import java.math.BigDecimal;

/**
 * CloudWatch 수집 결과 + DB 저장용 중간 DTO
 */
public record ComputeMetricDto(
        ResourceType resourceType,
        String resourceId,    // EC2: instanceId  / ECS: clusterName
        String resourceName,  // EC2: instanceType / ECS: serviceName
        String clusterName,   // ECS 전용
        BigDecimal avgCpuPercent,
        BigDecimal maxCpuPercent,
        BigDecimal avgMemoryPercent,  // ECS 전용 (EC2는 null)
        BigDecimal maxMemoryPercent,  // ECS 전용 (EC2는 null)
        Integer desiredCount,         // ECS 전용
        Integer runningCount,         // ECS 전용
        WasteScore wasteScore
) {}
