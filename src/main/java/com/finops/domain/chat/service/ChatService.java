package com.finops.domain.chat.service;

import com.finops.common.context.TenantContext;
import com.finops.common.exception.CustomException;
import com.finops.common.exception.ErrorCode;
import com.finops.domain.chat.entity.ChatHistory;
import com.finops.domain.chat.repository.ChatHistoryRepository;
import com.finops.domain.cost.repository.CostRecordRepository;
import com.finops.infra.claude.ClaudeApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ClaudeApiClient claudeApiClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final CostRecordRepository costRecordRepository;

    @Transactional
    public String chat(String question) {
        Long tenantId = TenantContext.getTenantId();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) auth.getPrincipal();

        // ADMIN 심야 시간 제한 (01:00 ~ 04:00)
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            int hour = LocalTime.now().getHour();
            if (hour >= 1 && hour < 4) {
                throw new CustomException(ErrorCode.CHAT_TIME_RESTRICTED);
            }
        }

        // 최근 3개월 서비스별 비용 컨텍스트 구성
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(3);
        var costData = costRecordRepository.sumByService(tenantId, from, to);

        StringBuilder context = new StringBuilder("최근 3개월 AWS 비용 데이터:\n");
        costData.forEach(row -> context.append(String.format("- %s: $%.2f%n", row[0], row[1])));

        String answer = claudeApiClient.analyze(context.toString(), question);

        // 대화 이력 저장
        chatHistoryRepository.save(ChatHistory.builder()
                .tenantId(tenantId)
                .userId(userId)
                .question(question)
                .answer(answer)
                .costContext(context.toString())
                .build());

        return answer;
    }
}
