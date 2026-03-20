package com.finops.infra.aws;

import com.finops.common.exception.CustomException;
import com.finops.common.exception.ErrorCode;
import com.finops.domain.aws.entity.AwsIntegration;
import com.finops.domain.aws.repository.AwsIntegrationRepository;
import com.finops.domain.cost.entity.CostRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Cost Explorer API → FOCUS 표준으로 정규화.
 * 테넌트별 IAM Role을 AssumeRole로 위임받아 호출.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AwsCostExplorerClient {

    private final StsClient stsClient;
    private final AwsIntegrationRepository awsIntegrationRepository;

    public List<CostRecord> fetchAndNormalize(Long tenantId, LocalDate from, LocalDate to) {
        CostExplorerClient ceClient = buildClientForTenant(tenantId);
        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(from.toString())
                            .end(to.toString())
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics("BlendedCost", "UsageQuantity")
                    .groupBy(
                            GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key("SERVICE").build(),
                            GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key("REGION").build()
                    )
                    .build();

            GetCostAndUsageResponse response = ceClient.getCostAndUsage(request);
            return normalize(tenantId, response);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("AWS Cost Explorer API 호출 실패 - tenantId={}", tenantId, e);
            throw new CustomException(ErrorCode.AWS_API_ERROR);
        } finally {
            ceClient.close();
        }
    }

    /** 테넌트의 IAM Role을 AssumeRole하여 Cost Explorer 클라이언트 생성 */
    private CostExplorerClient buildClientForTenant(Long tenantId) {
        AwsIntegration integration = awsIntegrationRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new CustomException(ErrorCode.AWS_INTEGRATION_NOT_FOUND));

        if (integration.getStatus() != AwsIntegration.IntegrationStatus.CONNECTED) {
            throw new CustomException(ErrorCode.AWS_INTEGRATION_NOT_FOUND);
        }

        try {
            Credentials creds = stsClient.assumeRole(AssumeRoleRequest.builder()
                    .roleArn(integration.getRoleArn())
                    .roleSessionName("finops-sync-" + tenantId + "-" + System.currentTimeMillis())
                    .externalId(integration.getExternalId())
                    .durationSeconds(3600)
                    .build()).credentials();

            return CostExplorerClient.builder()
                    .region(Region.US_EAST_1) // Cost Explorer는 us-east-1 고정
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsSessionCredentials.create(
                                    creds.accessKeyId(),
                                    creds.secretAccessKey(),
                                    creds.sessionToken())))
                    .build();

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("AssumeRole 실패 - tenantId={}, roleArn={}", tenantId, integration.getRoleArn(), e);
            throw new CustomException(ErrorCode.AWS_ASSUME_ROLE_FAILED);
        }
    }

    private List<CostRecord> normalize(Long tenantId, GetCostAndUsageResponse response) {
        List<CostRecord> records = new ArrayList<>();
        for (ResultByTime result : response.resultsByTime()) {
            LocalDate periodStart = LocalDate.parse(result.timePeriod().start());
            LocalDate periodEnd   = LocalDate.parse(result.timePeriod().end());

            for (Group group : result.groups()) {
                String serviceName = group.keys().get(0);
                String regionName  = group.keys().size() > 1 ? group.keys().get(1) : "global";
                MetricValue costMetric  = group.metrics().get("BlendedCost");
                MetricValue usageMetric = group.metrics().get("UsageQuantity");

                BigDecimal cost = new BigDecimal(costMetric.amount());
                if (cost.compareTo(BigDecimal.ZERO) == 0) continue;

                records.add(CostRecord.builder()
                        .tenantId(tenantId)
                        .billingPeriodStart(periodStart)
                        .billingPeriodEnd(periodEnd)
                        .serviceName(serviceName)
                        .regionName(regionName)
                        .billedCost(cost)
                        .usageQuantity(usageMetric != null ? new BigDecimal(usageMetric.amount()) : BigDecimal.ZERO)
                        .billingCurrency("USD")
                        .chargeType("Usage")
                        .provider("AWS")
                        .build());
            }
        }
        return records;
    }
}
