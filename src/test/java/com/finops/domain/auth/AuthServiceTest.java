package com.finops.domain.auth;

import com.finops.common.exception.CustomException;
import com.finops.common.security.JwtUtil;
import com.finops.domain.auth.dto.LoginRequest;
import com.finops.domain.auth.service.AuthService;
import com.finops.domain.tenant.entity.Tenant;
import com.finops.domain.tenant.repository.TenantRepository;
import com.finops.domain.user.entity.User;
import com.finops.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks AuthService authService;
    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;

    @Test
    @DisplayName("로그인 성공 시 토큰 반환")
    void login_success() {
        // given
        Tenant tenant = Tenant.builder().id(1L).tenantCode("local-tenant").name("test").build();
        User user = User.builder().id(1L).tenantId(1L).email("admin@finops.dev")
                .password("encoded").role(User.Role.ADMIN).build();

        given(tenantRepository.findByTenantCode("local-tenant")).willReturn(Optional.of(tenant));
        given(userRepository.findByTenantIdAndEmail(1L, "admin@finops.dev")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Admin1234!", "encoded")).willReturn(true);
        given(jwtUtil.generateAccessToken(1L, 1L, "ADMIN")).willReturn("access-token");
        given(jwtUtil.generateRefreshToken(1L, 1L, "ADMIN")).willReturn("refresh-token");

        // when
        var result = authService.login(new LoginRequest("local-tenant", "admin@finops.dev", "Admin1234!"));

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("비밀번호 불일치 시 예외 발생")
    void login_wrongPassword() {
        Tenant tenant = Tenant.builder().id(1L).tenantCode("local-tenant").name("test").build();
        User user = User.builder().id(1L).tenantId(1L).email("admin@finops.dev")
                .password("encoded").role(User.Role.ADMIN).build();

        given(tenantRepository.findByTenantCode("local-tenant")).willReturn(Optional.of(tenant));
        given(userRepository.findByTenantIdAndEmail(1L, "admin@finops.dev")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("local-tenant", "admin@finops.dev", "wrong")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("비밀번호");
    }
}
