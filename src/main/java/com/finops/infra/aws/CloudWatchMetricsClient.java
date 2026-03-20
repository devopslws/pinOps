package com.finops.infra.aws;

import com.finops.domain.cost.dto.ComputeMetricDto;
import com.finops.domain.cost.entity.ComputeMetric;
import com.finops.domain.cost.entity.ComputeMetric.ResourceType;
import com.finops.domain.cost.entity.ComputeMetric.WasteScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudWatchMetricsClient {

    private final StsClient stsClient;

    private static final int PERIOD_7D_SECONDS  = 7 * 24 * 3600;
    private static final int PERIOD_24H_SECONDS = 24 * 3600;

    // ─────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────

    public List<ComputeMetricDto> fetchEc2Metrics(String roleArn, String externalId, String region) {
        Credentials creds = assumeRole(roleArn, externalId, "finops-cw-ec2");
        Region awsRegion = Region.of(region);

        List<ComputeMetricDto> result = new ArrayList<>();

        try (Ec2Client ec2 = buildEc2Client(creds, awsRegion);
             CloudWatchClient cw = buildCwClient(creds, awsRegion)) {

            ec2.describeInstances(r -> r.filters(
                    Filter.builder().name("instance-state-name").values("running").build()
            )).reservations().forEach(reservation ->
                    reservation.instances().forEach(instance -> {
                        String instanceId   = instance.instanceId();
                        String instanceType = instance.instanceTypeAsString();

                        double avg = queryStat("AWS/EC2", "InstanceId", instanceId,
                                "CPUUtilization", Statistic.AVERAGE, PERIOD_7D_SECONDS, cw);
                        double max = queryStat("AWS/EC2", "InstanceId", instanceId,
                                "CPUUtilization", Statistic.MAXIMUM, PERIOD_24H_SECONDS, cw);

                        result.add(new ComputeMetricDto(
                                ResourceType.EC2,
                                instanceId,
                                instanceType,
                                null,                          // clusterName
                                bd(avg), bd(max),
                                null, null,                    // 메모리 - EC2는 CW Agent 필요
                                null, null,                    // desiredCount, runningCount
                                ComputeMetric.calcWasteScore(avg, ResourceType.EC2)
                        ));
                    }));
        }

        log.info("[CloudWatch] EC2 메트릭 수집 완료: {}개", result.size());
        return result;
    }

    public List<ComputeMetricDto> fetchEcsMetrics(String roleArn, String externalId, String region) {
        Credentials creds = assumeRole(roleArn, externalId, "finops-cw-ecs");
        Region awsRegion = Region.of(region);

        List<ComputeMetricDto> result = new ArrayList<>();

        try (EcsClient ecs = buildEcsClient(creds, awsRegion);
             CloudWatchClient cw = buildCwClient(creds, awsRegion)) {

            List<String> clusterArns = ecs.listClusters().clusterArns();
            if (clusterArns.isEmpty()) return result;

            for (Cluster cluster : ecs.describeClusters(r -> r.clusters(clusterArns)).clusters()) {
                String clusterName = cluster.clusterName();

                List<String> serviceArns = ecs.listServices(r -> r.cluster(clusterName)).serviceArns();
                if (serviceArns.isEmpty()) continue;

                for (Service service : ecs.describeServices(r ->
                        r.cluster(clusterName).services(serviceArns)).services()) {

                    String serviceName = service.serviceName();

                    double avgCpu = queryEcsStat(clusterName, serviceName,
                            "CPUUtilization", Statistic.AVERAGE, PERIOD_7D_SECONDS, cw);
                    double maxCpu = queryEcsStat(clusterName, serviceName,
                            "CPUUtilization", Statistic.MAXIMUM, PERIOD_24H_SECONDS, cw);

                    // ECS는 메모리 메트릭 기본 제공
                    double avgMem = queryEcsStat(clusterName, serviceName,
                            "MemoryUtilization", Statistic.AVERAGE, PERIOD_7D_SECONDS, cw);
                    double maxMem = queryEcsStat(clusterName, serviceName,
                            "MemoryUtilization", Statistic.MAXIMUM, PERIOD_24H_SECONDS, cw);

                    result.add(new ComputeMetricDto(
                            ResourceType.ECS,
                            clusterName,
                            serviceName,
                            clusterName,
                            bd(avgCpu), bd(maxCpu),
                            bd(avgMem), bd(maxMem),
                            service.desiredCount(),
                            service.runningCount(),
                            ComputeMetric.calcWasteScore(avgCpu, ResourceType.ECS)
                    ));
                }
            }
        }

        log.info("[CloudWatch] ECS 메트릭 수집 완료: {}개", result.size());
        return result;
    }

    // ─────────────────────────────────────────────────────
    // Private helpers - CloudWatch 조회
    // ─────────────────────────────────────────────────────

    private double queryStat(String namespace, String dimName, String dimValue,
                              String metricName, Statistic stat, int periodSeconds,
                              CloudWatchClient cw) {
        try {
            return cw.getMetricStatistics(r -> r
                    .namespace(namespace)
                    .metricName(metricName)
                    .dimensions(Dimension.builder().name(dimName).value(dimValue).build())
                    .startTime(Instant.now().minus(7, ChronoUnit.DAYS))
                    .endTime(Instant.now())
                    .period(periodSeconds)
                    .statistics(stat)
            ).datapoints().stream()
                    .mapToDouble(dp -> stat == Statistic.AVERAGE ? dp.average() : dp.maximum())
                    .average().orElse(0.0);
        } catch (Exception e) {
            log.warn("[CloudWatch] 조회 실패 ({}/{}): {}", namespace, dimValue, e.getMessage());
            return 0.0;
        }
    }

    private double queryEcsStat(String cluster, String service, String metricName,
                                 Statistic stat, int periodSeconds, CloudWatchClient cw) {
        try {
            return cw.getMetricStatistics(r -> r
                    .namespace("AWS/ECS")
                    .metricName(metricName)
                    .dimensions(
                            Dimension.builder().name("ClusterName").value(cluster).build(),
                            Dimension.builder().name("ServiceName").value(service).build()
                    )
                    .startTime(Instant.now().minus(7, ChronoUnit.DAYS))
                    .endTime(Instant.now())
                    .period(periodSeconds)
                    .statistics(stat)
            ).datapoints().stream()
                    .mapToDouble(dp -> stat == Statistic.AVERAGE ? dp.average() : dp.maximum())
                    .average().orElse(0.0);
        } catch (Exception e) {
            log.warn("[CloudWatch] ECS 메트릭 조회 실패 ({}/{}): {}", cluster, service, e.getMessage());
            return 0.0;
        }
    }

    // ─────────────────────────────────────────────────────
    // Private helpers - 클라이언트 + 유틸
    // ─────────────────────────────────────────────────────

    private Credentials assumeRole(String roleArn, String externalId, String sessionName) {
        return stsClient.assumeRole(AssumeRoleRequest.builder()
                .roleArn(roleArn).roleSessionName(sessionName)
                .externalId(externalId).durationSeconds(900).build()).credentials();
    }

    private StaticCredentialsProvider credentialsOf(Credentials c) {
        return StaticCredentialsProvider.create(
                AwsSessionCredentials.create(c.accessKeyId(), c.secretAccessKey(), c.sessionToken()));
    }

    private CloudWatchClient buildCwClient(Credentials c, Region r) {
        return CloudWatchClient.builder().region(r).credentialsProvider(credentialsOf(c)).build();
    }

    private Ec2Client buildEc2Client(Credentials c, Region r) {
        return Ec2Client.builder().region(r).credentialsProvider(credentialsOf(c)).build();
    }

    private EcsClient buildEcsClient(Credentials c, Region r) {
        return EcsClient.builder().region(r).credentialsProvider(credentialsOf(c)).build();
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
