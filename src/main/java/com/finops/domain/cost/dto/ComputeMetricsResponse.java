package com.finops.domain.cost.dto;

import java.util.List;
import java.util.Map;

public record ComputeMetricsResponse(
        List<Map<String, Object>> ec2,
        List<Map<String, Object>> ecs,
        int totalResources,
        long underutilizedCount  // 과소사용 리소스 수 (절감 권장 후보)
) {}
