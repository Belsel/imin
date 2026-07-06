package com.imin.backend.chat;

import com.imin.backend.chat.dto.GroupChatMessageResponse;
import com.imin.backend.chat.dto.PostGroupChatMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Group chat — plain REST polling only, no WebSocket/SSE (spec.md Chats,
 * design.md §5: "no special backend infrastructure"). Nested under
 * {@code /api/groups/{groupId}/messages}, consistent with
 * {@code GroupController}'s nested-resource convention for group-scoped
 * actions.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/messages")
@RequiredArgsConstructor
public class GroupChatController {

    private final GroupChatService groupChatService;

    @GetMapping
    public ResponseEntity<List<GroupChatMessageResponse>> getMessages(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable Long groupId,
                                                                        @RequestParam(required = false) Long after) {
        return ResponseEntity.ok(groupChatService.getMessages(jwt.getSubject(), groupId, after));
    }

    @PostMapping
    public ResponseEntity<GroupChatMessageResponse> postMessage(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable Long groupId,
                                                                  @Valid @RequestBody PostGroupChatMessageRequest request) {
        return ResponseEntity.ok(groupChatService.postMessage(jwt.getSubject(), groupId, request));
    }
}
