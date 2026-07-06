package com.imin.backend.group;

import com.imin.backend.group.dto.AddGroupCategoryRequest;
import com.imin.backend.group.dto.CreateGroupRequest;
import com.imin.backend.group.dto.GroupBanResponse;
import com.imin.backend.group.dto.GroupMemberResponse;
import com.imin.backend.group.dto.GroupRecommendationResponse;
import com.imin.backend.group.dto.GroupResponse;
import com.imin.backend.group.dto.UpdateGroupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 20;

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(groupService.createGroup(jwt.getSubject(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroup(jwt.getSubject(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                       @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(groupService.updateGroup(jwt.getSubject(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        groupService.deleteGroup(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<GroupResponse>> searchGroups(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam(required = false) String q) {
        return ResponseEntity.ok(groupService.searchGroups(jwt.getSubject(), q));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GroupResponse>> getMyGroups(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(groupService.getMyGroups(jwt.getSubject()));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<GroupRecommendationResponse>> recommendGroups(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) Integer limit) {
        int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_RECOMMENDATION_LIMIT : limit;
        return ResponseEntity.ok(groupService.recommendGroups(jwt.getSubject(), latitude, longitude, effectiveLimit));
    }

    @PostMapping("/{id}/categories")
    public ResponseEntity<GroupResponse> addCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                       @Valid @RequestBody AddGroupCategoryRequest request) {
        return ResponseEntity.ok(groupService.addCategory(jwt.getSubject(), id, request.categoryId()));
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    public ResponseEntity<GroupResponse> removeCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                          @PathVariable Long categoryId) {
        return ResponseEntity.ok(groupService.removeCategory(jwt.getSubject(), id, categoryId));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<GroupMemberResponse>> listMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(groupService.listMembers(jwt.getSubject(), id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<GroupResponse> joinGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(groupService.joinGroup(jwt.getSubject(), id));
    }

    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<Void> leaveGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        groupService.leaveGroup(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> kickMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                            @PathVariable Long userId) {
        groupService.kickMember(jwt.getSubject(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/bans")
    public ResponseEntity<List<GroupBanResponse>> listBans(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(groupService.listBans(jwt.getSubject(), id));
    }

    @PostMapping("/{id}/bans/{userId}")
    public ResponseEntity<Void> banMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                          @PathVariable Long userId) {
        groupService.banMember(jwt.getSubject(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/bans/{userId}")
    public ResponseEntity<Void> unbanMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                             @PathVariable Long userId) {
        groupService.unbanMember(jwt.getSubject(), id, userId);
        return ResponseEntity.noContent().build();
    }
}
