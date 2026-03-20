package com.finops.infra.claude;

import com.finops.common.exception.CustomException;
import com.finops.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Groq API 클라이언트 (OpenAI 호환 포맷).
 * llama-3.3-70b-versatile 모델로 비용 분석 수행 (무료 티어).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeApiClient {

    private final WebClient claudeWebClient;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    public String analyze(String costContext, String question) {

        String systemPrompt = """
                당신은 AWS 클라우드 비용 최적화 전문가입니다.
                아래 제공되는 비용 데이터를 바탕으로 사용자의 질문에 답변하세요.
                절감 방안, 이상 비용, 트렌드 분석을 구체적으로 제시하세요.

                %s
                """.formatted(costContext);

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", question)
                )
        );

        try {
            var response = claudeWebClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) throw new RuntimeException("Groq API 응답 없음");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (CustomException e) {
            throw e;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("Groq API 호출 실패 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.CLAUDE_API_ERROR);
        } catch (Exception e) {
            log.error("Groq API 호출 실패", e);
            throw new CustomException(ErrorCode.CLAUDE_API_ERROR);
        }
    }
}
