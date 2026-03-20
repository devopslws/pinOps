package com.finops.domain.chat.controller;

import com.finops.common.response.ApiResponse;
import com.finops.domain.chat.dto.ChatRequest;
import com.finops.domain.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")  // VIEWER 접근 차단
    public ApiResponse<String> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.chat(request.question()));
    }
}
