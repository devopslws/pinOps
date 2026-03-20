package com.finops.domain.cost.controller;

import com.finops.common.response.ApiResponse;
import com.finops.domain.cost.dto.CostSummaryResponse;
import com.finops.domain.cost.service.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/costs")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    /**
     * AWS에서 비용 데이터 동기화 (ADMIN, EDITOR만 가능)
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ApiResponse<Integer> sync(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int count = costService.syncFromAws(from, to);
        return ApiResponse.ok(count + "건 동기화 완료", count);
    }

    /**
     * 비용 요약 대시보드 데이터
     */
    @GetMapping("/summary")
    public ApiResponse<CostSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(costService.getSummary(from, to));
    }
}
