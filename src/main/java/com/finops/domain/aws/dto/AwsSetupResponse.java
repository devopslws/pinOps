package com.finops.domain.aws.dto;

public record AwsSetupResponse(
        String externalId,
        String cfnTemplateYaml,   // 콘솔에 붙여넣거나 다운로드
        String cfnConsoleUrl,     // 원클릭 배포 링크
        String status
) {}
