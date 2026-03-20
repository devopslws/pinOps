package com.finops.domain.auth.service;

import com.finops.common.aop.Auditable;
import com.finops.common.exception.CustomException;
import com.finops.common.exception.ErrorCode;
import com.finops.common.security.JwtUtil;
import com.finops.domain.auth.dto.LoginRequest;
import com.finops.domain.auth.dto.SignupRequest;
import com.finops.domain.auth.dto.TokenResponse;
import com.finops.domain.tenant.entity.Tenant;
import com.finops.domain.tenant.repository.TenantRepository;
import com.finops.domain.user.entity.User;
import com.finops.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    @Auditable(action = "SIGNUP", resource = "AUTH")
    public void signup(SignupRequest request) {
        Tenant tenant = tenantRepository.findByTenantCode(request.tenantCode())
                .orElseThrow(() -> new CustomException(ErrorCode.TENANT_NOT_FOUND));

        if (userRepository.existsByTenantIdAndEmail(tenant.getId(), request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .tenantId(tenant.getId())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(User.Role.VIEWER)
                .build();

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Auditable(action = "LOGIN", resource = "AUTH")
    public TokenResponse login(LoginRequest request) {
        Tenant tenant = tenantRepository.findByTenantCode(request.tenantCode())
                .orElseThrow(() -> new CustomException(ErrorCode.TENANT_NOT_FOUND));

        User user = userRepository.findByTenantIdAndEmail(tenant.getId(), request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), tenant.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), tenant.getId(), user.getRole().name());

        return TokenResponse.of(accessToken, refreshToken);
    }
}
