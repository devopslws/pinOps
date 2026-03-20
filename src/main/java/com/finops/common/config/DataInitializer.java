package com.finops.common.config;

import com.finops.domain.tenant.entity.Tenant;
import com.finops.domain.tenant.repository.TenantRepository;
import com.finops.domain.user.entity.User;
import com.finops.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 이미 초기화된 경우 skip
        if (tenantRepository.findByTenantCode("local-tenant").isPresent()) {
            log.info("[DataInitializer] 초기 데이터 이미 존재 → skip");
            return;
        }

        // 테넌트 생성
        Tenant tenant = tenantRepository.save(
                Tenant.builder()
                        .tenantCode("local-tenant")
                        .name("로컬 테스트 테넌트")
                        .build()
        );
        log.info("[DataInitializer] Tenant 생성 완료: id={}", tenant.getId());

        // 관리자 계정 생성 (비밀번호: qwer1234)
        User admin = userRepository.save(
                User.builder()
                        .tenantId(tenant.getId())
                        .email("admin@finops.dev")
                        .password(passwordEncoder.encode("qwer1234"))
                        .role(User.Role.ADMIN)
                        .build()
        );
        log.info("[DataInitializer] Admin 계정 생성 완료: email={}", admin.getEmail());

        // 일반 사용자(실무자) 계정 생성 - AI 채팅 접근 불가
        User viewer = userRepository.save(
                User.builder()
                        .tenantId(tenant.getId())
                        .email("user@finops.dev")
                        .password(passwordEncoder.encode("qwer1234"))
                        .role(User.Role.VIEWER)
                        .build()
        );
        log.info("[DataInitializer] Viewer 계정 생성 완료: email={}", viewer.getEmail());
    }
}
