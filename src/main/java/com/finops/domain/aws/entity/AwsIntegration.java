package com.finops.domain.aws.entity;

import com.finops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "aws_integrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AwsIntegration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    /** 보안용 무작위 식별자 (Confused Deputy 방지) */
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    /** 고객 AWS 계정의 IAM Role ARN */
    @Column(name = "role_arn", length = 200)
    private String roleArn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IntegrationStatus status = IntegrationStatus.PENDING;

    public enum IntegrationStatus {
        PENDING,    // CFN 배포 전
        CONNECTED,  // AssumeRole 검증 완료
        FAILED      // 검증 실패
    }

    public void connect(String roleArn) {
        this.roleArn = roleArn;
        this.status = IntegrationStatus.CONNECTED;
    }

    public void markFailed() {
        this.status = IntegrationStatus.FAILED;
    }
}
