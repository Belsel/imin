package com.imin.backend.user;

import com.imin.backend.category.CategoryService;
import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.category.dto.UpdateCategoryPreferencesRequest;
import com.imin.backend.user.dto.ProfileResponse;
import com.imin.backend.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CategoryService categoryService;

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getProfile(jwt.getSubject()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ProfileResponse> updateMe(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(jwt.getSubject(), request));
    }

    @GetMapping("/me/category-preferences")
    public ResponseEntity<List<CategoryResponse>> getCategoryPreferences(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(categoryService.getPreferences(jwt.getSubject()));
    }

    @PutMapping("/me/category-preferences")
    public ResponseEntity<List<CategoryResponse>> updateCategoryPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateCategoryPreferencesRequest request) {
        return ResponseEntity.ok(categoryService.updatePreferences(jwt.getSubject(), request));
    }
}
