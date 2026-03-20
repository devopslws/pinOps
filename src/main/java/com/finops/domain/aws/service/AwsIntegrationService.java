package com.finops.domain.aws.service;

import com.finops.common.exception.CustomException;
import com.finops.common.exception.ErrorCode;
import com.finops.domain.aws.dto.AwsConnectRequest;
import com.finops.domain.aws.dto.AwsConnectResponse;
import com.finops.domain.aws.dto.AwsSetupResponse;
import com.finops.domain.aws.entity.AwsIntegration;
import com.finops.domain.aws.repository.AwsIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsIntegrationService {

    private final AwsIntegrationRepository awsIntegrationRepository;
    private final StsClient stsClient;

    /**
     * Step 1: ExternalId 발급 + CloudFormation 템플릿 생성
     */
    @Transactional
    public AwsSetupResponse generateSetup(Long tenantId) {
        // 이미 연동된 경우 기존 정보 반환
        AwsIntegration integration = awsIntegrationRepository.findByTenantId(tenantId)
                .orElseGet(() -> awsIntegrationRepository.save(
                        AwsIntegration.builder()
                                .tenantId(tenantId)
                                .externalId(UUID.randomUUID().toString())
                                .build()
                ));

        // 우리 서비스 계정의 ARN 조회 (CFN trust policy에 사용)
        String callerArn = getCallerArn();
        String cfnYaml = buildCfnTemplate(callerArn, integration.getExternalId());
        String cfnConsoleUrl = buildCfnConsoleUrl(cfnYaml);

        return new AwsSetupResponse(
                integration.getExternalId(),
                cfnYaml,
                cfnConsoleUrl,
                integration.getStatus().name()
        );
    }

    /**
     * Step 2: Role ARN 입력 → AssumeRole 검증 → 연동 완료
     */
    @Transactional
    public AwsConnectResponse connect(Long tenantId, AwsConnectRequest request) {
        AwsIntegration integration = awsIntegrationRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new CustomException(ErrorCode.AWS_INTEGRATION_NOT_FOUND));

        try {
            AssumeRoleResponse assumeResponse = stsClient.assumeRole(AssumeRoleRequest.builder()
                    .roleArn(request.roleArn())
                    .roleSessionName("finops-validate-" + tenantId)
                    .externalId(integration.getExternalId())
                    .durationSeconds(900) // 검증용 최소값
                    .build());

            String assumedArn = assumeResponse.assumedRoleUser().arn();
            integration.connect(request.roleArn());
            log.info("[AwsIntegration] 테넌트 {} 연동 성공: {}", tenantId, assumedArn);

            return new AwsConnectResponse("CONNECTED", request.roleArn(), assumedArn, "AWS 연동이 완료되었습니다.");

        } catch (Exception e) {
            integration.markFailed();
            log.warn("[AwsIntegration] 테넌트 {} AssumeRole 실패: {}", tenantId, e.getMessage());
            throw new CustomException(ErrorCode.AWS_ASSUME_ROLE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public AwsSetupResponse getStatus(Long tenantId) {
        AwsIntegration integration = awsIntegrationRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new CustomException(ErrorCode.AWS_INTEGRATION_NOT_FOUND));

        String callerArn = getCallerArn();
        String cfnYaml = buildCfnTemplate(callerArn, integration.getExternalId());

        return new AwsSetupResponse(
                integration.getExternalId(),
                cfnYaml,
                buildCfnConsoleUrl(cfnYaml),
                integration.getStatus().name()
        );
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private String getCallerArn() {
        try {
            GetCallerIdentityResponse identity = stsClient.getCallerIdentity();
            return identity.arn(); // arn:aws:iam::123456789012:user/myuser
        } catch (Exception e) {
            log.warn("[AwsIntegration] STS GetCallerIdentity 실패 - dummy ARN 사용: {}", e.getMessage());
            throw new CustomException(ErrorCode.AWS_CREDENTIALS_NOT_CONFIGURED);
        }
    }

    private String buildCfnTemplate(String trustedArn, String externalId) {
        return """
AWSTemplateFormatVersion: '2010-09-09'
Description: FinOps Cost & Compute Reader Role

Resources:
  FinOpsCostReaderRole:
    Type: AWS::IAM::Role
    Properties:
      RoleName: finops-cost-reader
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              AWS: '%s'
            Action: sts:AssumeRole
            Condition:
              StringEquals:
                sts:ExternalId: '%s'
      Policies:
        - PolicyName: FinOpsCostReaderPolicy
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              # 비용 조회
              - Effect: Allow
                Action:
                  - ce:GetCostAndUsage
                  - ce:GetCostForecast
                  - ce:GetDimensionValues
                  - ce:GetRightsizingRecommendation
                Resource: '*'
              # EC2 인스턴스 인벤토리 (읽기 전용)
              - Effect: Allow
                Action:
                  - ec2:DescribeInstances
                  - ec2:DescribeInstanceTypes
                  - ec2:DescribeRegions
                Resource: '*'
              # ECS 클러스터/서비스/태스크 인벤토리 (읽기 전용)
              - Effect: Allow
                Action:
                  - ecs:ListClusters
                  - ecs:DescribeClusters
                  - ecs:ListServices
                  - ecs:DescribeServices
                  - ecs:ListTasks
                  - ecs:DescribeTasks
                  - ecs:DescribeContainerInstances
                Resource: '*'
              # CloudWatch 메트릭 (CPU 사용률 등)
              - Effect: Allow
                Action:
                  - cloudwatch:GetMetricData
                  - cloudwatch:GetMetricStatistics
                  - cloudwatch:ListMetrics
                Resource: '*'
              # Compute Optimizer 절감 추천
              - Effect: Allow
                Action:
                  - compute-optimizer:GetEC2InstanceRecommendations
                  - compute-optimizer:GetECSServiceRecommendations
                  - compute-optimizer:GetRecommendationSummaries
                Resource: '*'

Outputs:
  RoleArn:
    Description: Copy this Role ARN into the FinOps connection form
    Value: !GetAtt FinOpsCostReaderRole.Arn
""".formatted(trustedArn, externalId);
    }

    /** AWS CloudFormation 콘솔 원클릭 배포 링크 생성 */
    private String buildCfnConsoleUrl(String templateBody) {
        // 실제 운영에서는 S3에 업로드 후 URL 사용. 데모에서는 Quick Create 링크
        String encoded = URLEncoder.encode(templateBody, StandardCharsets.UTF_8);
        // templateBody가 너무 길면 URL이 잘리므로, 실제론 S3 URL을 써야 함
        return "https://console.aws.amazon.com/cloudformation/home#/stacks/create/review"
                + "?stackName=finops-cost-reader";
    }
}
