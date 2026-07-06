package com.imin.backend.config;

import com.imin.backend.auth.EmailVerificationTokenRepository;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.group.GroupBanRepository;
import com.imin.backend.group.GroupMembershipRepository;
import com.imin.backend.group.GroupRepository;
import com.imin.backend.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the real-servlet-container error-dispatch bug
 * (see spec.md/design.md "Implementation notes" for {@code ApiExceptionHandler}):
 * any non-2xx response used to trigger Tomcat's internal forward to
 * {@code /error}, which re-entered the Spring Security filter chain and
 * either overwrote the real status with a bare {@code 401} (on
 * {@code permitAll()} paths) or replaced the body with Spring Boot's
 * generic default error payload (on authenticated paths).
 *
 * <p>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} +
 * {@link TestRestTemplate} is used deliberately instead of {@code MockMvc}:
 * it starts a real embedded Tomcat and issues real HTTP requests over a real
 * socket, so it actually exercises {@code sendError()}/{@code /error}-forward
 * semantics the way a browser hitting the deployed app would. {@code MockMvc}
 * cannot reproduce this — it never performs a container-level error dispatch
 * — which is exactly why 161 passing {@code MockMvc} tests missed this bug
 * in the first place. These tests fail without {@code ApiExceptionHandler}
 * (confirmed during development: removing it reproduces the corrupted
 * {@code 401}/generic-body symptoms here, while the equivalent
 * {@code MockMvc}-based tests elsewhere keep passing either way) and pass
 * with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ApiExceptionHandlerContainerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMembershipRepository membershipRepository;

    @Autowired
    private GroupBanRepository banRepository;

    @BeforeEach
    void setUp() {
        banRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * The exact symptom from the bug report: {@code POST /api/auth/register}
     * is a {@code permitAll()} path. A duplicate email makes
     * {@code AuthService.register} throw {@code ResponseStatusException(409)}.
     * Before the fix, the real container's {@code /error} forward re-entered
     * the security chain as an unauthenticated request and corrupted this
     * into a bare {@code 401} with an empty body. With
     * {@code ApiExceptionHandler} in place (and no {@code sendError()} call,
     * so no {@code /error} forward happens at all), the real {@code 409} and
     * the real message must survive end-to-end over a real socket.
     */
    @Test
    void duplicateEmailRegistrationOverRealContainerReturns409WithMessageNot401() {
        RegisterRequest request = new RegisterRequest(
                "container-dup@example.com", "password123", "Container Dup");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> first = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(toJson(request), headers), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(toJson(request), headers), String.class);

        assertThat(second.getStatusCode())
                .as("a duplicate-email 409 must not be corrupted into a 401 by the /error forward")
                .isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = readJson(second.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("message").asText())
                .isEqualTo("An account with this email already exists");
    }

    /**
     * Login with a wrong password throws {@code BadCredentialsException}
     * from {@code AuthService} — a normal {@code @Service} call path, not
     * the Spring Security filter chain — so it would never reach an
     * {@code AuthenticationEntryPoint}. Confirms it now resolves to a clean
     * {@code 401} with a real message over the real container, rather than
     * an unhandled-exception {@code 500}.
     */
    @Test
    void loginWithWrongPasswordOverRealContainerReturns401WithMessage() {
        RegisterRequest request = new RegisterRequest(
                "container-badpw@example.com", "password123", "Container BadPw");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(toJson(request), headers), String.class);

        LoginRequest badLogin = new LoginRequest("container-badpw@example.com", "totally-wrong-password");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(toJson(badLogin), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = readJson(response.getBody());
        assertThat(body.get("message").asText()).isEqualTo("Invalid email or password");
    }

    /**
     * Authenticated-path counterpart of the bug: a banned user trying to
     * rejoin a group hits {@code GroupService}'s
     * {@code ResponseStatusException(FORBIDDEN, "You are banned from this
     * group")}. Before the fix, the original status code happened to survive
     * the {@code /error} forward on authenticated paths, but the body was
     * always replaced by Spring Boot's generic default error payload
     * ({@code timestamp/status/error/path}, no real message). Confirms the
     * real message now survives over the real container.
     */
    @Test
    void bannedGroupMemberAttemptingToRejoinOverRealContainerGetsTheRealMessageNotAGenericBody() {
        String adminJwt = registerVerifyAndLogin("container-admin@example.com", "Container Admin");
        String bannedJwt = registerVerifyAndLogin("container-banned@example.com", "Container Banned");

        Long groupId = createGroup(adminJwt, "Container Ban Test Group");
        joinGroup(bannedJwt, groupId);
        banMember(adminJwt, groupId, bannedJwt);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bannedJwt);
        ResponseEntity<String> rejoinAttempt = restTemplate.exchange(
                "/api/groups/" + groupId + "/members",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);

        assertThat(rejoinAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = readJson(rejoinAttempt.getBody());
        assertThat(body.get("message").asText())
                .as("the real exception message must survive, not Spring Boot's generic default error body")
                .isEqualTo("You are banned from this group");
        assertThat(body.has("path"))
                .as("must not be Spring Boot's default error body shape ({timestamp,status,error,path})")
                .isFalse();
    }

    private String registerVerifyAndLogin(String email, String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        RegisterRequest registerRequest = new RegisterRequest(email, "password123", displayName);
        ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(toJson(registerRequest), headers), String.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String token = verificationTokenRepository.findAll().stream()
                .filter(t -> !t.isUsed())
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToken();
        ResponseEntity<String> verifyResponse = restTemplate.getForEntity(
                "/api/auth/verify-email?token=" + token, String.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginRequest loginRequest = new LoginRequest(email, "password123");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(toJson(loginRequest), headers), String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(loginResponse.getBody()).get("token").asText();
    }

    private Long createGroup(String creatorJwt, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(creatorJwt);
        String requestBody = "{\"name\":\"" + name + "\",\"description\":\"d\","
                + "\"latitude\":1.0,\"longitude\":1.0,\"categoryIds\":[]}";
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/groups", new HttpEntity<>(requestBody, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return readJson(response.getBody()).get("id").asLong();
    }

    private void joinGroup(String memberJwt, Long groupId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(memberJwt);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/groups/" + groupId + "/members",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void banMember(String adminJwt, Long groupId, String memberJwt) {
        HttpHeaders memberAuthHeaders = new HttpHeaders();
        memberAuthHeaders.setBearerAuth(memberJwt);
        ResponseEntity<String> meResponse = restTemplate.exchange(
                "/api/users/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(memberAuthHeaders), String.class);
        Long memberId = readJson(meResponse.getBody()).get("id").asLong();

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminJwt);
        ResponseEntity<String> banResponse = restTemplate.exchange(
                "/api/groups/" + groupId + "/bans/" + memberId,
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(adminHeaders),
                String.class);
        assertThat(banResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(String body) {
        return objectMapper.readTree(body);
    }
}
