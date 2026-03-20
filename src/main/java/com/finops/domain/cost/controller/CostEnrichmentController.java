package com.finops.domain.cost.controller;

import com.finops.common.context.TenantContext;
import com.finops.common.response.ApiResponse;
import com.finops.domain.cost.dto.ServiceInsightDto;
import com.finops.domain.cost.service.CostEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/costs")
@RequiredArgsConstructor
public class CostEnrichmentController {

    private final CostEnrichmentService costEnrichmentService;

    /**
     * 비용 + 컴퓨팅 사용률 통합 인사이트
     * - 메트릭 수집 후 조회 권장 (POST /api/v1/metrics/compute/collect 선행)
     * - Claude 분석에 이 데이터를 컨텍스트로 전달하면 절감 플랜 제안 가능
     */
    @GetMapping("/enriched")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public ApiResponse<List<ServiceInsightDto>> getEnriched() {
        Long tenantId = TenantContext.getTenantId();
        return ApiResponse.ok(costEnrichmentService.getEnrichedInsights(tenantId));
    }
}
