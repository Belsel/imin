package com.imin.backend.auth;

import com.imin.backend.auth.dto.AuthResponse;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.auth.dto.RegisterResponse;
import com.imin.backend.email.EmailService;
import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Service-level tests for {@link AuthService}, exercised against a real
 * (H2 in-memory) {@link UserRepository}/{@link EmailVerificationTokenRepository}.
 * Only {@link EmailService} is mocked, since it's the seam that keeps
 * registration testable without a live network call to Resend.
 *
 * Covers spec.md (Users section) acceptance criteria:
 * - LOCAL registration creates an unverified account and triggers a
 *   verification email; the account cannot log in until verified.
 * - The verification token expires after 24h; expired/invalid/already-used
 *   tokens are rejected.
 * - Account name (email) is unique and immutable (no update path exists).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanState() {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesUnverifiedLocalUserAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123", "Alice");

        RegisterResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.emailVerified()).isFalse();

        User stored = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertThat(stored.isEmailVerified()).isFalse();
        assertThat(stored.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(stored.getEmail()).isEqualTo("alice@example.com");

        // Verification email was sent via the EmailService seam (no live network call made).
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).send(anyString(), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("/verify-email?token=");

        // A 24h-expiry verification token was persisted for this user.
        var tokens = verificationTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        EmailVerificationToken token = tokens.get(0);
        assertThat(token.getUserId()).isEqualTo(stored.getId());
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
        assertThat(token.getExpiresAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        authService.register(new RegisterRequest("bob@example.com", "password123", "Bob"));

        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("bob@example.com", "password456", "Bob2")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        // Only one user row exists — duplicate registration did not create a second account.
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void loginRejectsUnverifiedLocalAccount() {
        authService.register(new RegisterRequest("carol@example.com", "password123", "Carol"));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("carol@example.com", "password123")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void loginSucceedsAfterEmailVerification() {
        authService.register(new RegisterRequest("dave@example.com", "password123", "Dave"));
        String token = verificationTokenRepository.findAll().get(0).getToken();

        authService.verifyEmail(token);

        AuthResponse response = authService.login(new LoginRequest("dave@example.com", "password123"));
        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");

        User stored = userRepository.findByEmail("dave@example.com").orElseThrow();
        assertThat(stored.isEmailVerified()).isTrue();
    }

    @Test
    void loginRejectsWrongPasswordRegardlessOfVerificationState() {
        authService.register(new RegisterRequest("erin@example.com", "password123", "Erin"));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("erin@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyEmailRejectsInvalidToken() {
        assertThatThrownBy(() -> authService.verifyEmail("not-a-real-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void verifyEmailRejectsAlreadyUsedToken() {
        authService.register(new RegisterRequest("frank@example.com", "password123", "Frank"));
        String token = verificationTokenRepository.findAll().get(0).getToken();

        authService.verifyEmail(token); // first use succeeds

        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        authService.register(new RegisterRequest("grace@example.com", "password123", "Grace"));
        EmailVerificationToken token = verificationTokenRepository.findAll().get(0);

        // Force-expire the token (simulates the 24h TTL having elapsed).
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        verificationTokenRepository.save(token);

        assertThatThrownBy(() -> authService.verifyEmail(token.getToken()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // Account remains unverified, and login is still rejected.
        User stored = userRepository.findByEmail("grace@example.com").orElseThrow();
        assertThat(stored.isEmailVerified()).isFalse();
        assertThatThrownBy(() -> authService.login(new LoginRequest("grace@example.com", "password123")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void accountNameIsTheEmailAndIsUniqueAcrossUsers() {
        authService.register(new RegisterRequest("henry@example.com", "password123", "Henry"));

        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("henry@example.com", "differentPassword1", "Someone Else")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        User stored = userRepository.findByEmail("henry@example.com").orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("henry@example.com");
    }
}
