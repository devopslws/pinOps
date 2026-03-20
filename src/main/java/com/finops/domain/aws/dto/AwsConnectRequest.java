package com.finops.domain.aws.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AwsConnectRequest(
        @NotBlank
        @Pattern(regexp = "arn:aws:iam::\\d{12}:role/.+", message = "올바른 Role ARN 형식이 아닙니다.")
        String roleArn
) {}
