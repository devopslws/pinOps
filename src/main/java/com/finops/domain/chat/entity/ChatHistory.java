package com.finops.domain.chat.entity;

import com.finops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_history",
       indexes = @Index(name = "idx_chat_tenant_user", columnList = "tenant_id, user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChatHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    // 비용 컨텍스트 요약 (Claude에 전달했던 데이터 스냅샷)
    @Column(name = "cost_context", columnDefinition = "JSON")
    private String costContext;
}
