package com.finops.domain.cost.dto;

import com.finops.domain.cost.entity.ComputeMetric.WasteScore;

import java.math.BigDecimal;

/**
 * 비용 + 컴퓨팅 사용률 통합 뷰
 * Claude AI 분석 컨텍스트로도 활용
 */
public record ServiceInsightDto(
        String serviceName,           // AmazonEC2, AmazonECS 등
        BigDecimal totalCost,         // 최근 30일 비용 합계
        String currency,

        // 컴퓨팅 메트릭 (EC2/ECS만 해당, 나머지는 null)
        BigDecimal avgCpuPercent,
        BigDecimal maxCpuPercent,
        BigDecimal avgMemoryPercent,  // ECS만
        BigDecimal maxMemoryPercent,  // ECS만

        WasteScore wasteScore,        // 절감 권장 등급
        String recommendation         // 간단한 한줄 권장사항
) {}
