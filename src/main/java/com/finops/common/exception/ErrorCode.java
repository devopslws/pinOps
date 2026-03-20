package com.finops.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),

    // Tenant
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "테넌트를 찾을 수 없습니다."),
    TENANT_CONTEXT_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "테넌트 컨텍스트가 설정되지 않았습니다."),

    // AWS
    AWS_API_ERROR(HttpStatus.BAD_GATEWAY, "AWS API 호출 중 오류가 발생했습니다."),
    AWS_INTEGRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AWS 연동 정보가 없습니다. 먼저 연동을 설정하세요."),
    AWS_ASSUME_ROLE_FAILED(HttpStatus.BAD_GATEWAY, "AWS Role Assume에 실패했습니다. Role ARN과 권한을 확인하세요."),
    AWS_CREDENTIALS_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "AWS 기본 자격증명이 설정되지 않았습니다."),

    // Claude / Chat
    CLAUDE_API_ERROR(HttpStatus.BAD_GATEWAY, "Claude API 호출 중 오류가 발생했습니다."),
    CHAT_TIME_RESTRICTED(HttpStatus.FORBIDDEN, "심야 시간(01:00~04:00)에는 AI 채팅을 사용할 수 없습니다."),

    // General
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
