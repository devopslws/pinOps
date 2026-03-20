package com.finops.domain.aws.dto;

public record AwsConnectResponse(
        String status,      // CONNECTED | FAILED
        String roleArn,
        String assumedArn,  // AssumeRole로 받은 임시 자격증명의 ARN (검증용)
        String message
) {}
