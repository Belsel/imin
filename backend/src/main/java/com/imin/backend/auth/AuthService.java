package com.imin.backend.auth;

import com.imin.backend.auth.dto.AuthResponse;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.auth.dto.RegisterResponse;
import com.imin.backend.email.EmailService;
import com.imin.backend.security.JwtService;
import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long VERIFICATION_TOKEN_TTL_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(false);
        userRepository.save(user);

        sendVerificationEmail(user);

        return RegisterResponse.pendingVerification(user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (user.getProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Email not verified. Check your inbox for the verification link.");
        }
        return issueToken(user);
    }

    /**
     * Passwordless auto-login for the shared "Try demo account" entry point
     * (see specs/try-demo-account/spec.md). Looks up the demo user by the
     * demo flag rather than the configured email — the authoritative "is this
     * the demo account" signal is the flag on the row, not whatever
     * {@code demo.account.email} currently resolves to, so this stays in sync
     * with the restriction-enforcement checks that key off the same flag.
     * Reuses {@link #issueToken(User)} verbatim so the issued JWT has exactly
     * the same claim shape as a normal login.
     */
    public AuthResponse demoLogin() {
        User user = userRepository.findFirstByDemoAccountTrue()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Demo account is not provisioned"));
        return issueToken(user);
    }

    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token already used");
        }
        if (verificationToken.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token expired");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsedAt(Instant.now());
        verificationTokenRepository.save(verificationToken);
    }

    private void sendVerificationEmail(User user) {
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUserId(user.getId());
        verificationToken.setToken(UUID.randomUUID().toString());
        verificationToken.setExpiresAt(Instant.now().plus(VERIFICATION_TOKEN_TTL_HOURS, ChronoUnit.HOURS));
        verificationTokenRepository.save(verificationToken);

        String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken.getToken();
        String body = "<p>Welcome to ImIn! Click the link below to verify your email address:</p>"
                + "<p><a href=\"" + verificationLink + "\">" + verificationLink + "</a></p>"
                + "<p>This link expires in " + VERIFICATION_TOKEN_TTL_HOURS + " hours.</p>";

        emailService.send(user.getEmail(), "Verify your ImIn email address", body);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getEmail(), Map.of(
                "uid", user.getId(),
                "name", user.getDisplayName()
        ));
        return AuthResponse.bearer(token, jwtService.expirySeconds());
    }
}
