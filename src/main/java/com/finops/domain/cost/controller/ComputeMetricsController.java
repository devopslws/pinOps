package com.finops.domain.cost.controller;

import com.finops.common.context.TenantContext;
import com.finops.common.response.ApiResponse;
import com.finops.domain.cost.dto.ComputeMetricsResponse;
import com.finops.domain.cost.service.ComputeMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class ComputeMetricsController {

    private final ComputeMetricsService computeMetricsService;

    /**
     * CloudWatch 실시간 수집 → DB 저장 → 결과 반환
     * AWS 연동(CFN Role) CONNECTED 상태여야 동작
     */
    @PostMapping("/compute/collect")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ApiResponse<ComputeMetricsResponse> collect() {
        Long tenantId = TenantContext.getTenantId();
        return ApiResponse.ok("메트릭 수집 완료", computeMetricsService.collectAndSave(tenantId));
    }

    /**
     * DB에서 최근 스냅샷 조회 (AWS 재호출 없음)
     */
    @GetMapping("/compute")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public ApiResponse<ComputeMetricsResponse> getLatest() {
        Long tenantId = TenantContext.getTenantId();
        return ApiResponse.ok(computeMetricsService.getLatest(tenantId));
    }
}
