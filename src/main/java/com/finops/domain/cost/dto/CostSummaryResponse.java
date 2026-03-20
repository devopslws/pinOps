package com.finops.domain.cost.dto;

import java.math.BigDecimal;
import java.util.List;

public record CostSummaryResponse(
        BigDecimal totalCost,
        String currency,
        List<ServiceCost> byService,
        List<MonthlyCost> byMonth
) {
    public record ServiceCost(String serviceName, BigDecimal cost) {}
    public record MonthlyCost(String yearMonth, BigDecimal cost) {}
}
