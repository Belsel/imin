package com.imin.backend.chat;

import com.imin.backend.auth.EmailVerificationTokenRepository;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.group.GroupBanRepository;
import com.imin.backend.group.GroupMembershipRepository;
import com.imin.backend.group.GroupRepository;
import com.imin.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for {@code GroupChatController}, exercising the real
 * security filter chain (JWT bearer auth) plus bean validation, which the
 * service-level {@link GroupChatServiceTest} cannot reach (calling the
 * service directly bypasses {@code @Valid}). Confirms: a real member can
 * post/poll, a blank message is rejected at the HTTP layer, and there is no
 * way to submit anything but a plain-text {@code body} field (no attachment
 * field is accepted/echoed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
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
    @Autowired
    private GroupChatMessageRepository messageRepository;

    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        banRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        aliceJwt = registerVerifyAndLogin("chat-alice@example.com", "Alice");
        bobJwt = registerVerifyAndLogin("chat-bob@example.com", "Bob");
    }

    private String registerVerifyAndLogin(String email, String displayName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123", displayName))))
                .andExpect(status().isOk());
        String token = verificationTokenRepository.findAll().stream()
                .filter(t -> !t.isUsed())
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token)).andExpect(status().isOk());

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginBody).get("token").asText();
    }

    private Long createGroupViaApi(String creatorJwt) throws Exception {
        String body = mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + creatorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HTTP Chat Group\",\"description\":\"d\",\"latitude\":1.0,\"longitude\":1.0,\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void memberCanPostAndPollMessagesOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hello chat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("hello chat"));

        mockMvc.perform(get("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("hello chat"));
    }

    @Test
    void blankMessageBodyIsRejected() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyMessageBodyIsRejected() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonMemberIsRejectedOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(get("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"sneaking in\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(get("/api/groups/" + groupId + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postingAnAttachmentFieldIsSilentlyIgnoredNotStoredOrEchoed() throws Exception {
        // PostGroupChatMessageRequest has no attachment/file/url field at all
        // (see dto javadoc) -- Jackson silently ignores any unknown extra
        // property in the JSON body, so submitting one neither errors nor
        // results in any such data being persisted/returned.
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"text only\",\"attachmentUrl\":\"http://example.com/file.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("text only"))
                .andExpect(jsonPath("$.attachmentUrl").doesNotExist());
    }
}
