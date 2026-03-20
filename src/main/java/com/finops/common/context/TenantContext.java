package com.finops.common.context;

/**
 * 요청 스레드에 테넌트 ID를 바인딩하는 ThreadLocal 컨텍스트.
 * JwtAuthFilter에서 설정, 모든 Service 레이어에서 읽음.
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext가 초기화되지 않았습니다.");
        }
        return tenantId;
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
