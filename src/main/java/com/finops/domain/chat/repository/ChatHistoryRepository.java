package com.finops.domain.chat.repository;

import com.finops.domain.chat.entity.ChatHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    Page<ChatHistory> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            Long tenantId, Long userId, Pageable pageable);
}
