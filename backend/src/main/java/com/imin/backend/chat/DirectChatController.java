package com.imin.backend.chat;

import com.imin.backend.chat.dto.DirectMessageResponse;
import com.imin.backend.chat.dto.DirectThreadResponse;
import com.imin.backend.chat.dto.PostDirectMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Direct (1:1) chat — plain REST polling only, no WebSocket/SSE, mirroring
 * {@link GroupChatController} (spec.md Direct chats: "delivered via client
 * polling, consistent with group chat"). Not gated by friend status in any
 * way — see {@link DirectChatService} javadoc.
 */
@RestController
@RequiredArgsConstructor
public class DirectChatController {

    private final DirectChatService directChatService;

    @GetMapping("/api/dm/threads")
    public ResponseEntity<List<DirectThreadResponse>> listThreads(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(directChatService.listThreads(jwt.getSubject()));
    }

    @GetMapping("/api/dm/{userId}/messages")
    public ResponseEntity<List<DirectMessageResponse>> getMessages(@AuthenticationPrincipal Jwt jwt,
                                                                      @PathVariable Long userId,
                                                                      @RequestParam(required = false) Long after) {
        return ResponseEntity.ok(directChatService.getMessages(jwt.getSubject(), userId, after));
    }

    @PostMapping("/api/dm/{userId}/messages")
    public ResponseEntity<DirectMessageResponse> sendMessage(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable Long userId,
                                                                @Valid @RequestBody PostDirectMessageRequest request) {
        return ResponseEntity.ok(directChatService.sendMessage(jwt.getSubject(), userId, request));
    }
}
