package com.imin.backend.auth;

import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for {@code POST /api/auth/demo-login}, exercised
 * through the real Spring Security filter chain (MockMvc), mirroring {@link
 * AuthFlowIntegrationTest}'s pattern for the normal login flow. Covers
 * specs/try-demo-account/spec.md's "Entry point / auto-login" and "Security"
 * acceptance criteria:
 * <ul>
 *   <li>The endpoint returns a valid JWT with no request body/credentials,
 *   and that JWT authenticates subsequent requests as the demo user.</li>
 *   <li>The issued JWT has exactly the same claim shape as a normal user's
 *   JWT — no additional role/scope claim.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoLoginFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${demo.account.email}")
    private String demoEmail;
    @Value("${demo.account.password}")
    private String demoPassword;
    @Value("${demo.account.display-name}")
    private String demoDisplayName;

    @BeforeEach
    void ensureDemoUserSeeded() {
        // The real DemoUserSeeder already runs once at application startup;
        // this is a defensive, idempotent fallback so this test is
        // self-sufficient even if some other, non-transactional test class
        // sharing this test JVM's single named H2 instance (e.g.
        // AuthFlowIntegrationTest, which wipes all users outside a
        // transaction) happened to run first and removed the row.
        if (userRepository.findFirstByDemoAccountTrue().isEmpty()) {
            User demoUser = new User();
            demoUser.setEmail(demoEmail);
            demoUser.setPasswordHash(passwordEncoder.encode(demoPassword));
            demoUser.setDisplayName(demoDisplayName);
            demoUser.setProvider(AuthProvider.LOCAL);
            demoUser.setEmailVerified(true);
            demoUser.setDemoAccount(true);
            userRepository.save(demoUser);
        }
    }

    @Test
    void demoLoginIssuesUsableJwtWithNoRequestBodyOrCredentials() throws Exception {
        String body = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        String jwt = objectMapper.readTree(body).get("token").asText();
        assertThat(jwt).isNotBlank();

        // The issued JWT authenticates subsequent requests as the demo user.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(demoEmail))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void demoLoginNeverReturnsEmailNotVerified403ViaHttp() throws Exception {
        // Same criterion as DemoUserSeederTest, but through the actual
        // /api/auth/login HTTP path with the demo account's fixed
        // credentials, rather than calling AuthService directly.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(demoEmail, demoPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void demoJwtHasSameClaimShapeAsNormalUserJwtNoElevatedClaims() throws Exception {
        // Demo JWT via the new passwordless endpoint.
        String demoBody = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String demoJwt = objectMapper.readTree(demoBody).get("token").asText();

        // A normal (non-demo) user's JWT via the standard login endpoint.
        User normalUser = new User();
        normalUser.setEmail("normal-claims-check@example.com");
        normalUser.setPasswordHash(passwordEncoder.encode("password123"));
        normalUser.setDisplayName("Normal User");
        normalUser.setProvider(AuthProvider.LOCAL);
        normalUser.setEmailVerified(true);
        userRepository.save(normalUser);

        String normalBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("normal-claims-check@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String normalJwt = objectMapper.readTree(normalBody).get("token").asText();

        Set<String> demoClaimNames = decodeClaims(demoJwt).keySet();
        Set<String> normalClaimNames = decodeClaims(normalJwt).keySet();

        // Same claim set (iss/iat/exp/sub/uid/name) -- no new elevated
        // role/scope claim introduced for the demo account.
        assertThat(demoClaimNames).isEqualTo(normalClaimNames);
        assertThat(demoClaimNames).containsExactlyInAnyOrder("iss", "iat", "exp", "sub", "uid", "name");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeClaims(String jwt) {
        String[] parts = jwt.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readValue(payloadJson, Map.class);
    }
}
