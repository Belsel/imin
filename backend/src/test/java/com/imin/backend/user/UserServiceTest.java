package com.imin.backend.user;

import com.imin.backend.user.dto.ProfileResponse;
import com.imin.backend.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level tests for {@link UserService}, exercised against a real
 * (H2 in-memory) {@link UserRepository}.
 *
 * Covers spec.md acceptance criteria for the profile slice:
 * - Each account has a display name, distinct from the account name, that
 *   the user can change at any time without a uniqueness constraint.
 * - A user can set, edit, and clear a biography/description on their own
 *   profile.
 * - The account name (email) is exposed in the profile but there is no
 *   update path for it (PATCH only accepts displayName/bio).
 *
 * Partial-update semantics under test: {@code displayName} and {@code bio}
 * are independently optional on {@link UpdateProfileRequest} — a null field
 * leaves the corresponding entity field untouched, so updating one never
 * blanks the other. An explicit empty-string {@code bio} clears it; a
 * blank {@code displayName} (when present) is rejected, since display name
 * can never be cleared to empty.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setEmail("profile-user@example.com");
        testUser.setPasswordHash("hash");
        testUser.setDisplayName("Profile User");
        testUser.setProvider(AuthProvider.LOCAL);
        testUser.setEmailVerified(true);
        userRepository.save(testUser);
    }

    @Test
    void getProfileReturnsAccountNameAsEmail() {
        ProfileResponse profile = userService.getProfile("profile-user@example.com");

        assertThat(profile.email()).isEqualTo("profile-user@example.com");
        assertThat(profile.displayName()).isEqualTo("Profile User");
        assertThat(profile.bio()).isNull();
        assertThat(profile.emailVerified()).isTrue();
    }

    @Test
    void userCanSetBio() {
        ProfileResponse updated = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest(null, "Loves hiking and board games."));

        assertThat(updated.bio()).isEqualTo("Loves hiking and board games.");

        User reloaded = userRepository.findByEmail("profile-user@example.com").orElseThrow();
        assertThat(reloaded.getBio()).isEqualTo("Loves hiking and board games.");
    }

    @Test
    void userCanEditBio() {
        userService.updateProfile("profile-user@example.com", new UpdateProfileRequest(null, "Initial bio."));
        ProfileResponse updated = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest(null, "Edited bio."));

        assertThat(updated.bio()).isEqualTo("Edited bio.");
    }

    @Test
    void userCanClearBioWithEmptyString() {
        userService.updateProfile("profile-user@example.com", new UpdateProfileRequest(null, "Some bio text."));

        ProfileResponse cleared = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest(null, ""));

        assertThat(cleared.bio()).isNull();
        User reloaded = userRepository.findByEmail("profile-user@example.com").orElseThrow();
        assertThat(reloaded.getBio()).isNull();
    }

    @Test
    void updateProfileDoesNotExposeAnEmailChangePath() {
        // UpdateProfileRequest has no email field at all — this test documents/locks in
        // that the account name (email) is immutable by construction (no setter is
        // reachable through this DTO), consistent with spec.md's immutability requirement.
        userService.updateProfile("profile-user@example.com", new UpdateProfileRequest(null, "bio text"));

        User reloaded = userRepository.findByEmail("profile-user@example.com").orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("profile-user@example.com");
    }

    @Test
    void userCanChangeDisplayName() {
        ProfileResponse updated = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest("New Name", null));

        assertThat(updated.displayName()).isEqualTo("New Name");

        User reloaded = userRepository.findByEmail("profile-user@example.com").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    void updatingDisplayNameAloneLeavesBioUnchanged() {
        userService.updateProfile("profile-user@example.com", new UpdateProfileRequest(null, "Existing bio."));

        ProfileResponse updated = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest("New Name", null));

        assertThat(updated.displayName()).isEqualTo("New Name");
        assertThat(updated.bio()).isEqualTo("Existing bio.");
    }

    @Test
    void updatingBioAloneLeavesDisplayNameUnchanged() {
        ProfileResponse updated = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest(null, "A fresh bio."));

        assertThat(updated.bio()).isEqualTo("A fresh bio.");
        assertThat(updated.displayName()).isEqualTo("Profile User");
    }

    @Test
    void userCanUpdateDisplayNameAndBioTogether() {
        ProfileResponse updated = userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest("Both Fields", "Both updated at once."));

        assertThat(updated.displayName()).isEqualTo("Both Fields");
        assertThat(updated.bio()).isEqualTo("Both updated at once.");

        User reloaded = userRepository.findByEmail("profile-user@example.com").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("Both Fields");
        assertThat(reloaded.getBio()).isEqualTo("Both updated at once.");
    }

    @Test
    void blankDisplayNameIsRejected() {
        assertThatThrownBy(() -> userService.updateProfile("profile-user@example.com",
                new UpdateProfileRequest("   ", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Display name cannot be blank");

        User reloaded = userRepository.findByEmail("profile-user@example.com").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("Profile User");
    }
}
