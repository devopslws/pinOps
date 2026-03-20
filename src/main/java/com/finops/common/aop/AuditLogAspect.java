package com.finops.common.aop;

import com.finops.common.context.TenantContext;
import com.finops.domain.audit.entity.AuditLog;
import com.finops.domain.audit.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @Auditable 어노테이션이 붙은 메서드를 AOP로 감싸 audit_logs에 자동 기록.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String result = "SUCCESS";
        try {
            Object ret = joinPoint.proceed();
            return ret;
        } catch (Throwable t) {
            result = "FAILURE";
            throw t;
        } finally {
            saveLog(auditable, result);
        }
    }

    private void saveLog(Auditable auditable, String result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = (auth != null && auth.getPrincipal() instanceof Long)
                    ? (Long) auth.getPrincipal() : null;

            Long tenantId = null;
            try { tenantId = TenantContext.getTenantId(); } catch (Exception ignored) {}

            String ip = resolveIp();

            AuditLog auditLog = AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .action(auditable.action())
                    .resource(auditable.resource())
                    .ipAddress(ip)
                    .result(result)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // 감사 로그 실패가 비즈니스 로직에 영향을 주면 안 됨
            log.error("AuditLog 저장 실패", e);
        }
    }

    private String resolveIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes()).getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            return (xff != null) ? xff.split(",")[0].trim() : request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
