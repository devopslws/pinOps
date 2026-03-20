-- ============================================================
-- V1: 초기 스키마 생성
-- FinOps 멀티테넌트 기반 테이블 설계
-- ============================================================

CREATE TABLE tenants (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(50)  NOT NULL UNIQUE COMMENT '테넌트 고유 코드 (ex. acme-corp)',
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|SUSPENDED|DELETED',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    email       VARCHAR(200) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'VIEWER' COMMENT 'ADMIN|EDITOR|VIEWER',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_tenant_email (tenant_id, email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- FOCUS 표준 기반 비용 레코드
CREATE TABLE cost_records (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT         NOT NULL,
    billing_period_start DATE           NOT NULL COMMENT 'FOCUS: BillingPeriodStart',
    billing_period_end   DATE           NOT NULL COMMENT 'FOCUS: BillingPeriodEnd',
    service_name         VARCHAR(100)   NOT NULL COMMENT 'FOCUS: ServiceName',
    service_category     VARCHAR(50)    COMMENT 'FOCUS: ServiceCategory',
    region_name          VARCHAR(50)    COMMENT 'FOCUS: RegionName',
    billed_cost          DECIMAL(15, 6) NOT NULL COMMENT 'FOCUS: BilledCost',
    usage_quantity       DECIMAL(15, 6) COMMENT 'FOCUS: UsageQuantity',
    usage_unit           VARCHAR(50)    COMMENT 'FOCUS: UsageUnit',
    billing_currency     VARCHAR(10)    NOT NULL DEFAULT 'USD' COMMENT 'FOCUS: BillingCurrency',
    resource_id          VARCHAR(200)   COMMENT 'FOCUS: ResourceId',
    charge_type          VARCHAR(50)    COMMENT 'FOCUS: ChargeType',
    provider             VARCHAR(20)    NOT NULL DEFAULT 'AWS',
    created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_cost_tenant_period (tenant_id, billing_period_start),
    INDEX idx_cost_service (tenant_id, service_name),
    CONSTRAINT fk_cost_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE chat_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    question     TEXT   NOT NULL,
    answer       TEXT   NOT NULL,
    cost_context JSON   COMMENT '분석에 사용된 비용 컨텍스트 스냅샷',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_chat_tenant_user (tenant_id, user_id),
    CONSTRAINT fk_chat_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_chat_user   FOREIGN KEY (user_id)   REFERENCES users (id)
);

CREATE TABLE audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT      COMMENT 'NULL 허용: 테넌트 확정 전 로그 (ex. 로그인 실패)',
    user_id     BIGINT      COMMENT 'NULL 허용: 인증 전 요청',
    action      VARCHAR(50) NOT NULL COMMENT 'LOGIN|LOGOUT|COST_SYNC|CHAT 등',
    resource    VARCHAR(100),
    ip_address  VARCHAR(50),
    result      VARCHAR(20) NOT NULL COMMENT 'SUCCESS|FAILURE',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_audit_tenant_created (tenant_id, created_at),
    INDEX idx_audit_user (user_id)
);

-- 초기 테넌트 & 관리자 계정 (개발용)
-- 비밀번호: Admin1234! (BCrypt 해시)
INSERT INTO tenants (tenant_code, name, status)
VALUES ('local-tenant', '로컬 테스트 테넌트', 'ACTIVE');

INSERT INTO users (tenant_id, email, password, role)
VALUES (1, 'admin@finops.dev', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');
