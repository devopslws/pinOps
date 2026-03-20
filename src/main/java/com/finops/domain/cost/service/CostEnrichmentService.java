package com.finops.domain.cost.service;

import com.finops.domain.cost.dto.ServiceInsightDto;
import com.finops.domain.cost.entity.ComputeMetric;
import com.finops.domain.cost.entity.ComputeMetric.WasteScore;
import com.finops.domain.cost.repository.ComputeMetricRepository;
import com.finops.domain.cost.repository.CostRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostEnrichmentService {

    private final CostRecordRepository   costRecordRepository;
    private final ComputeMetricRepository computeMetricRepository;

    // Cost Explorer 서비스명 → CloudWatch 리소스타입 매핑
    // Cost Explorer 실제 반환값 기준 (AWS 콘솔에서 확인한 full name)
    private static final Map<String, ComputeMetric.ResourceType> SERVICE_TYPE_MAP = Map.of(
            "Amazon Elastic Compute Cloud - Compute", ComputeMetric.ResourceType.EC2,
            "Amazon Elastic Container Service",       ComputeMetric.ResourceType.ECS
    );

    @Transactional(readOnly = true)
    public List<ServiceInsightDto> getEnrichedInsights(Long tenantId) {
        // 1. 최근 30일 서비스별 비용 합계
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to   = LocalDate.now();
        List<Object[]> costRows = costRecordRepository.sumByService(tenantId, from, to);

        // 2. DB에서 최근 컴퓨팅 메트릭 스냅샷
        List<ComputeMetric> metrics = computeMetricRepository.findLatestByTenantId(tenantId);

        // 서비스별 메트릭 집계 (EC2/ECS 여러 인스턴스 → 평균)
        Map<String, AvgMetric> metricByService = aggregateByService(metrics);

        // 3. 비용 + 메트릭 조인
        List<ServiceInsightDto> result = new ArrayList<>();
        for (Object[] row : costRows) {
            String serviceName  = (String) row[0];
            BigDecimal cost     = (BigDecimal) row[1];

            AvgMetric metric = metricByService.get(serviceName);

            result.add(new ServiceInsightDto(
                    serviceName,
                    cost,
                    "USD",
                    metric != null ? metric.avgCpu  : null,
                    metric != null ? metric.maxCpu  : null,
                    metric != null ? metric.avgMem  : null,
                    metric != null ? metric.maxMem  : null,
                    metric != null ? metric.waste   : WasteScore.UNKNOWN,
                    metric != null ? buildRecommendation(serviceName, cost, metric) : "컴퓨팅 메트릭 미수집"
            ));
        }

        return result;
    }

    // ─────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────

    /** EC2/ECS 여러 리소스의 메트릭을 서비스명 기준으로 평균 집계 */
    private Map<String, AvgMetric> aggregateByService(List<ComputeMetric> metrics) {
        // resourceType → 서비스명 역매핑
        Map<ComputeMetric.ResourceType, String> typeToService = SERVICE_TYPE_MAP.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        return metrics.stream()
                .filter(m -> typeToService.containsKey(m.getResourceType()))
                .collect(Collectors.groupingBy(
                        m -> typeToService.get(m.getResourceType()),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            double avgCpu = list.stream()
                                    .filter(m -> m.getAvgCpuPercent() != null)
                                    .mapToDouble(m -> m.getAvgCpuPercent().doubleValue())
                                    .average().orElse(0.0);
                            double maxCpu = list.stream()
                                    .filter(m -> m.getMaxCpuPercent() != null)
                                    .mapToDouble(m -> m.getMaxCpuPercent().doubleValue())
                                    .max().orElse(0.0);
                            double avgMem = list.stream()
                                    .filter(m -> m.getAvgMemoryPercent() != null)
                                    .mapToDouble(m -> m.getAvgMemoryPercent().doubleValue())
                                    .average().orElse(0.0);
                            double maxMem = list.stream()
                                    .filter(m -> m.getMaxMemoryPercent() != null)
                                    .mapToDouble(m -> m.getMaxMemoryPercent().doubleValue())
                                    .max().orElse(0.0);
                            WasteScore worst = list.stream()
                                    .map(ComputeMetric::getWasteScore)
                                    .max(java.util.Comparator.comparingInt(WasteScore::ordinal))
                                    .orElse(WasteScore.UNKNOWN);
                            return new AvgMetric(
                                    bd(avgCpu), bd(maxCpu),
                                    avgMem > 0 ? bd(avgMem) : null,
                                    maxMem > 0 ? bd(maxMem) : null,
                                    worst
                            );
                        })
                ));
    }

    private String buildRecommendation(String service, BigDecimal cost, AvgMetric m) {
        if (m.waste == WasteScore.HIGH) {
            return String.format(
                    "CPU 평균 %.1f%% - 인스턴스 다운사이징 또는 Savings Plan 검토 권장 (월 최대 $%.0f 절감 가능)",
                    m.avgCpu.doubleValue(), cost.doubleValue() * 0.3);
        }
        if (m.waste == WasteScore.MEDIUM) {
            return String.format("CPU 평균 %.1f%% - Reserved Instance 전환 시 약 20~30%% 절감 가능",
                    m.avgCpu.doubleValue());
        }
        return String.format("CPU 평균 %.1f%% - 적정 사용 중", m.avgCpu.doubleValue());
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private record AvgMetric(
            BigDecimal avgCpu, BigDecimal maxCpu,
            BigDecimal avgMem, BigDecimal maxMem,
            WasteScore waste
    ) {}
}
