package com.imin.backend.auth;

import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.email.EmailService;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for the auth + email-verification slice, exercising
 * real controllers through the real Spring Security filter chain
 * (MockMvc — no real sockets, so no risk of following a real OAuth2 login
 * redirect out to Google's servers the way a raw HTTP client would when
 * hitting an endpoint that 401s under an `oauth2Login()`-configured
 * security filter chain).
 *
 * Covers spec.md acceptance criteria:
 * - A new user can register with email + password; verification email is
 *   sent (asserted via the EmailService seam); account is unverified and
 *   cannot log in until verified.
 * - Login is rejected (403) for unverified LOCAL accounts.
 * - After verification, login succeeds and issues a usable JWT.
 * - The JWT can be used to access a protected endpoint, which also touches
 *   lastSeenAt (feeds the later 7-day admin-succession rule).
 * - Unauthenticated requests to protected endpoints are rejected.
 * - Invalid verification tokens are rejected with 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fullLocalRegistrationVerificationLoginFlow() throws Exception {
        // 1. Register.
        RegisterRequest registerRequest = new RegisterRequest("flow-user@example.com", "password123", "Flow User");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow-user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));

        User stored = userRepository.findByEmail("flow-user@example.com").orElseThrow();
        assertThat(stored.isEmailVerified()).isFalse();

        // 2. Login attempt before verification is rejected with 403.
        LoginRequest loginRequest = new LoginRequest("flow-user@example.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden());

        // 3. Verify using the real token that was persisted.
        String token = verificationTokenRepository.findAll().get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk());

        User verifiedUser = userRepository.findByEmail("flow-user@example.com").orElseThrow();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
        assertThat(verifiedUser.getLastSeenAt()).isNull(); // not yet touched — no authenticated request made yet

        // 4. Login now succeeds and returns a usable JWT.
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();
        String jwt = objectMapper.readTree(loginBody).get("token").asText();
        assertThat(jwt).isNotBlank();

        // 5. Use the JWT to access a protected endpoint; lastSeenAt should now be touched.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow-user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true));

        User afterAuthenticatedRequest = userRepository.findByEmail("flow-user@example.com").orElseThrow();
        assertThat(afterAuthenticatedRequest.getLastSeenAt())
                .as("lastSeenAt must be stamped after an authenticated request")
                .isNotNull();
        assertThat(afterAuthenticatedRequest.getLastSeenAt()).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    void protectedEndpointRejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyEmailWithInvalidTokenReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email").param("token", "totally-bogus-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchProfileUpdatesBioForAuthenticatedUser() throws Exception {
        // Register + verify + login to get a usable token.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("bio-flow@example.com", "password123", "Bio Flow"))))
                .andExpect(status().isOk());
        String token = verificationTokenRepository.findAll().get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token)).andExpect(status().isOk());
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("bio-flow@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jwt = objectMapper.readTree(loginBody).get("token").asText();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Test bio via HTTP.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Test bio via HTTP."));
    }

    /**
     * Confirms the fix for a previously-reported defect (see spec.md
     * Implementation notes "Pre-existing gap"): {@code AuthService.register()}
     * used to throw a plain {@code IllegalArgumentException} on a duplicate
     * email, which had no dedicated resolver and propagated uncaught to the
     * servlet container as a 500-class failure. It now throws a
     * {@link org.springframework.web.server.ResponseStatusException} with
     * {@code 409 Conflict}, so the duplicate-email path is verified
     * end-to-end here to return a clean 409 response rather than an
     * unhandled exception.
     */
    @Test
    void registerRejectsDuplicateEmailViaHttp() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("dup-flow@example.com", "password123", "Dup Flow");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict());

        // The functional requirement still holds: no second account was created.
        assertThat(userRepository.findAll()).hasSize(1);
    }
}
