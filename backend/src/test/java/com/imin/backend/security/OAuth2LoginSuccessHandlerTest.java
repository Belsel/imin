package com.imin.backend.security;

import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies the post-review fix for the OAuth verification-gate bypass
 * (spec.md Acceptance Criteria / Implementation notes: "the just-fixed
 * OAuth bypass bug must not be reproducible").
 *
 * Repro scenario this guards against: a user registers LOCAL with email E
 * but never verifies it (account exists with provider=LOCAL,
 * emailVerified=false). They then sign in with Google using the same email
 * E. Before the fix, the handler reused the existing LOCAL row as-is and
 * unconditionally minted a JWT — completely bypassing AuthService.login()'s
 * "LOCAL + unverified" rejection. The fix converts such a row to
 * provider=GOOGLE, emailVerified=true at the moment of the Google sign-in,
 * treating Google's independent proof of email ownership as equivalent to
 * completing verification (matching the spec's own justification for why
 * brand-new GOOGLE signups skip verification entirely).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OAuth2LoginSuccessHandlerTest {

    @Autowired
    private OAuth2LoginSuccessHandler handler;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanState() {
        userRepository.deleteAll();
    }

    private OAuth2User googleUser(String email, String name) {
        return new DefaultOAuth2User(
                List.of(() -> "ROLE_USER"),
                Map.of("email", email, "name", name, "sub", "google-sub-id"),
                "email");
    }

    @Test
    void existingUnverifiedLocalAccountIsConvertedToVerifiedGoogleOnOAuthSignIn() throws Exception {
        // Arrange: a LOCAL account that registered but never verified its email.
        User local = new User();
        local.setEmail("bypass-target@example.com");
        local.setPasswordHash("irrelevant-hash");
        local.setDisplayName("Local Name");
        local.setProvider(AuthProvider.LOCAL);
        local.setEmailVerified(false);
        userRepository.save(local);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                googleUser("bypass-target@example.com", "Google Display Name"), null, List.of());

        // Act: the same email signs in via Google.
        handler.onAuthenticationSuccess(request, response, authentication);

        // Assert: the bug-fix behavior — account is converted, not left as a silent bypass.
        User reloaded = userRepository.findByEmail("bypass-target@example.com").orElseThrow();
        assertThat(reloaded.getProvider())
                .as("account must be converted to GOOGLE, not left as an unverified LOCAL bypass")
                .isEqualTo(AuthProvider.GOOGLE);
        assertThat(reloaded.isEmailVerified())
                .as("account must become verified as a result of the Google sign-in")
                .isTrue();

        // A JWT was still issued (the user is allowed to proceed, having now proven
        // ownership of the email via Google) — verify a redirect with a token occurred.
        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("/oauth2/callback?token=");
    }

    @Test
    void newGoogleUserIsCreatedVerifiedImmediately() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                googleUser("brand-new@example.com", "Brand New"), null, List.of());

        handler.onAuthenticationSuccess(request, response, authentication);

        User created = userRepository.findByEmail("brand-new@example.com").orElseThrow();
        assertThat(created.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.isEmailVerified()).isTrue();
    }

    @Test
    void alreadyVerifiedLocalAccountSigningInViaGoogleIsNotConvertedAwayFromLocal() throws Exception {
        // Deliberately-unchanged behavior per implementation notes: an existing LOCAL
        // row that is ALREADY verified is not touched by the reconciliation branch.
        User local = new User();
        local.setEmail("already-verified@example.com");
        local.setPasswordHash("irrelevant-hash");
        local.setDisplayName("Verified Local");
        local.setProvider(AuthProvider.LOCAL);
        local.setEmailVerified(true);
        userRepository.save(local);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                googleUser("already-verified@example.com", "Verified Local"), null, List.of());

        handler.onAuthenticationSuccess(request, response, authentication);

        User reloaded = userRepository.findByEmail("already-verified@example.com").orElseThrow();
        assertThat(reloaded.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(reloaded.isEmailVerified()).isTrue();
    }
}
