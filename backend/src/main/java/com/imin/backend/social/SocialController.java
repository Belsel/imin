package com.imin.backend.social;

import com.imin.backend.social.dto.BlockResponse;
import com.imin.backend.social.dto.FriendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Friends (one-directional follow-style add/unfriend) and blocks
 * (one-directional). Both are standalone relationships in MVP — neither
 * gates direct chat or any other feature (see spec.md Resolved Questions
 * items 1–3); the only thing that consults {@link Block} outside this
 * controller is {@code DirectChatService} at message-send time.
 */
@RestController
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @GetMapping("/api/friends")
    public ResponseEntity<List<FriendResponse>> listFriends(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(socialService.listFriends(jwt.getSubject()));
    }

    @PostMapping("/api/friends/{userId}")
    public ResponseEntity<Void> addFriend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        socialService.addFriend(jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/friends/{userId}")
    public ResponseEntity<Void> removeFriend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        socialService.removeFriend(jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/blocks")
    public ResponseEntity<List<BlockResponse>> listBlocks(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(socialService.listBlocks(jwt.getSubject()));
    }

    @PostMapping("/api/blocks/{userId}")
    public ResponseEntity<Void> blockUser(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        socialService.blockUser(jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/blocks/{userId}")
    public ResponseEntity<Void> unblockUser(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        socialService.unblockUser(jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }
}
