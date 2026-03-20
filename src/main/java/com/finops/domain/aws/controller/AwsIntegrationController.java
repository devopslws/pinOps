package com.finops.domain.aws.controller;

import com.finops.common.context.TenantContext;
import com.finops.common.response.ApiResponse;
import com.finops.domain.aws.dto.AwsConnectRequest;
import com.finops.domain.aws.dto.AwsConnectResponse;
import com.finops.domain.aws.dto.AwsSetupResponse;
import com.finops.domain.aws.service.AwsIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/aws/integration")
@RequiredArgsConstructor
public class AwsIntegrationController {

    private final AwsIntegrationService awsIntegrationService;

    /**
     * Step 1: ExternalId + CloudFormation 템플릿 조회
     * ADMIN만 연동 설정 가능
     */
    @GetMapping("/setup")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AwsSetupResponse> setup() {
        return ApiResponse.ok(
                awsIntegrationService.generateSetup(TenantContext.getTenantId())
        );
    }

    /**
     * Step 2: Role ARN 제출 → AssumeRole 검증 → 연동 완료
     */
    @PostMapping("/connect")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AwsConnectResponse> connect(@Valid @RequestBody AwsConnectRequest request) {
        return ApiResponse.ok(
                awsIntegrationService.connect(TenantContext.getTenantId(), request)
        );
    }

    /**
     * 현재 연동 상태 조회
     */
    @GetMapping("/status")
    public ApiResponse<AwsSetupResponse> status() {
        return ApiResponse.ok(
                awsIntegrationService.getStatus(TenantContext.getTenantId())
        );
    }
}
