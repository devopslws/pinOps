package com.finops.domain.cost.service;

import com.finops.common.context.TenantContext;
import com.finops.domain.cost.dto.CostSummaryResponse;
import com.finops.domain.cost.entity.CostRecord;
import com.finops.domain.cost.repository.CostRecordRepository;
import com.finops.infra.aws.AwsCostExplorerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostService {

    private final CostRecordRepository costRecordRepository;
    private final AwsCostExplorerClient costExplorerClient;

    /**
     * AWS Cost Explorer에서 데이터를 가져와 FOCUS 정규화 후 DB 저장.
     */
    @Transactional
    public int syncFromAws(LocalDate from, LocalDate to) {
        Long tenantId = TenantContext.getTenantId();
        List<CostRecord> records = costExplorerClient.fetchAndNormalize(tenantId, from, to);
        costRecordRepository.saveAll(records);
        return records.size();
    }

    @Transactional(readOnly = true)
    public CostSummaryResponse getSummary(LocalDate from, LocalDate to) {
        Long tenantId = TenantContext.getTenantId();

        List<Object[]> byService = costRecordRepository.sumByService(tenantId, from, to);
        List<Object[]> byMonth = costRecordRepository.sumByMonth(tenantId);

        BigDecimal total = byService.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CostSummaryResponse.ServiceCost> serviceCosts = byService.stream()
                .map(row -> new CostSummaryResponse.ServiceCost(
                        (String) row[0], (BigDecimal) row[1]))
                .toList();

        List<CostSummaryResponse.MonthlyCost> monthlyCosts = byMonth.stream()
                .map(row -> new CostSummaryResponse.MonthlyCost(
                        (String) row[0], (BigDecimal) row[1]))
                .toList();

        return new CostSummaryResponse(total, "USD", serviceCosts, monthlyCosts);
    }
}
