package com.finops.domain.cost.service;

import com.finops.common.exception.CustomException;
import com.finops.common.exception.ErrorCode;
import com.finops.domain.aws.entity.AwsIntegration;
import com.finops.domain.aws.repository.AwsIntegrationRepository;
import com.finops.domain.cost.dto.ComputeMetricDto;
import com.finops.domain.cost.dto.ComputeMetricsResponse;
import com.finops.domain.cost.entity.ComputeMetric;
import com.finops.domain.cost.repository.ComputeMetricRepository;
import com.finops.infra.aws.CloudWatchMetricsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeMetricsService {

    private final AwsIntegrationRepository  awsIntegrationRepository;
    private final ComputeMetricRepository   computeMetricRepository;
    private final CloudWatchMetricsClient   cloudWatchMetricsClient;

    @Value("${aws.resources-region:${aws.region:ap-northeast-2}}")
    private String resourcesRegion;

    /**
     * CloudWatch에서 수집 → DB 저장 → 응답 반환
     */
    @Transactional
    public ComputeMetricsResponse collectAndSave(Long tenantId) {
        AwsIntegration integration = getConnectedIntegration(tenantId);
        String roleArn    = integration.getRoleArn();
        String externalId = integration.getExternalId();
        LocalDateTime now = LocalDateTime.now();

        log.info("[ComputeMetrics] 테넌트 {} 메트릭 수집 시작", tenantId);

        List<ComputeMetricDto> ec2Dtos = cloudWatchMetricsClient.fetchEc2Metrics(roleArn, externalId, resourcesRegion);
        List<ComputeMetricDto> ecsDtos = cloudWatchMetricsClient.fetchEcsMetrics(roleArn, externalId, resourcesRegion);

        // DB 저장
        saveAll(tenantId, ec2Dtos, now);
        saveAll(tenantId, ecsDtos, now);

        long underutilized = countHigh(ec2Dtos) + countHigh(ecsDtos);
        log.info("[ComputeMetrics] 저장 완료 - EC2: {}건, ECS: {}건, 과소사용: {}건",
                ec2Dtos.size(), ecsDtos.size(), underutilized);

        return toResponse(ec2Dtos, ecsDtos, underutilized);
    }

    /**
     * DB에서 최근 수집 스냅샷 조회 (AWS 재호출 없음)
     */
    @Transactional(readOnly = true)
    public ComputeMetricsResponse getLatest(Long tenantId) {
        List<ComputeMetric> latest = computeMetricRepository.findLatestByTenantId(tenantId);

        List<ComputeMetricDto> ec2 = latest.stream()
                .filter(m -> m.getResourceType() == ComputeMetric.ResourceType.EC2)
                .map(this::toDto).toList();

        List<ComputeMetricDto> ecs = latest.stream()
                .filter(m -> m.getResourceType() == ComputeMetric.ResourceType.ECS)
                .map(this::toDto).toList();

        long underutilized = countHigh(ec2) + countHigh(ecs);
        return toResponse(ec2, ecs, underutilized);
    }

    // ─────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────

    private AwsIntegration getConnectedIntegration(Long tenantId) {
        return awsIntegrationRepository.findByTenantId(tenantId)
                .filter(i -> i.getStatus() == AwsIntegration.IntegrationStatus.CONNECTED)
                .orElseThrow(() -> new CustomException(ErrorCode.AWS_INTEGRATION_NOT_FOUND));
    }

    private void saveAll(Long tenantId, List<ComputeMetricDto> dtos, LocalDateTime collectedAt) {
        List<ComputeMetric> entities = dtos.stream().map(dto -> ComputeMetric.builder()
                .tenantId(tenantId)
                .resourceType(dto.resourceType())
                .resourceId(dto.resourceId())
                .resourceName(dto.resourceName())
                .clusterName(dto.clusterName())
                .avgCpuPercent(dto.avgCpuPercent())
                .maxCpuPercent(dto.maxCpuPercent())
                .avgMemoryPercent(dto.avgMemoryPercent())
                .maxMemoryPercent(dto.maxMemoryPercent())
                .desiredCount(dto.desiredCount())
                .runningCount(dto.runningCount())
                .wasteScore(dto.wasteScore())
                .collectedAt(collectedAt)
                .build()
        ).toList();
        computeMetricRepository.saveAll(entities);
    }

    private ComputeMetricDto toDto(ComputeMetric m) {
        return new ComputeMetricDto(
                m.getResourceType(), m.getResourceId(), m.getResourceName(),
                m.getClusterName(),
                m.getAvgCpuPercent(), m.getMaxCpuPercent(),
                m.getAvgMemoryPercent(), m.getMaxMemoryPercent(),
                m.getDesiredCount(), m.getRunningCount(),
                m.getWasteScore()
        );
    }

    private long countHigh(List<ComputeMetricDto> dtos) {
        return dtos.stream()
                .filter(d -> d.wasteScore() == ComputeMetric.WasteScore.HIGH)
                .count();
    }

    private ComputeMetricsResponse toResponse(List<ComputeMetricDto> ec2,
                                               List<ComputeMetricDto> ecs,
                                               long underutilized) {
        return new ComputeMetricsResponse(
                ec2.stream().map(this::toMap).toList(),
                ecs.stream().map(this::toMap).toList(),
                ec2.size() + ecs.size(),
                underutilized
        );
    }

    private java.util.Map<String, Object> toMap(ComputeMetricDto dto) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("resourceType",       dto.resourceType());
        map.put("resourceId",         dto.resourceId());
        map.put("resourceName",       dto.resourceName());
        if (dto.clusterName() != null)      map.put("clusterName",   dto.clusterName());
        map.put("avgCpuPercent",      dto.avgCpuPercent());
        map.put("maxCpuPercent",      dto.maxCpuPercent());
        if (dto.avgMemoryPercent() != null) map.put("avgMemoryPercent", dto.avgMemoryPercent());
        if (dto.maxMemoryPercent() != null) map.put("maxMemoryPercent", dto.maxMemoryPercent());
        if (dto.desiredCount() != null)     map.put("desiredCount",  dto.desiredCount());
        if (dto.runningCount() != null)     map.put("runningCount",  dto.runningCount());
        map.put("wasteScore",         dto.wasteScore());
        return map;
    }
}
