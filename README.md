# FinOps Dashboard

AWS 클라우드 비용을 조회·분석하고 AI로 질의할 수 있는 대시보드입니다.

## 🌐 호스팅

**http://43.200.13.224:8080**

| 계정 | 이메일 | 비밀번호 | 권한 |
|------|--------|----------|------|
| 관리자 | admin@finops.dev | Admin1234! | 비용 조회 · AI 질의 |
| 일반 | user@finops.dev | qwer1234 | 비용 조회만 |

## 주요 기능

- **비용 대시보드** — AWS Cost Explorer 연동, 서비스별 비용 집계
- **컴퓨팅 인사이트** — EC2 / ECS CPU 사용률 메트릭 시각화
- **AI 질의** — Groq(LLaMA 3.3) 기반 자연어 비용 분석
- **감사 로그** — AOP 기반 주요 액션 자동 기록
- **역할 기반 접근 제어** — ADMIN / EDITOR / VIEWER

## 기술 스택

`Java 17` `Spring Boot 3.3` `Spring Security` `AWS SDK v2` `Groq API` `H2` `GitHub Actions` `EC2`
