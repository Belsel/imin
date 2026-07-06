package com.imin.backend.chat;

import com.imin.backend.auth.EmailVerificationTokenRepository;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.social.BlockRepository;
import com.imin.backend.social.FriendshipRepository;
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
 * Full-stack HTTP tests for {@code DirectChatController}, exercising the
 * real security filter chain plus bean validation. The most important case
 * here is {@code noFriendAddRequiredToSendOverHttp} — confirms over real
 * HTTP (not just at the service layer) that there is no friend-gating
 * anywhere in the direct-message send path, and
 * {@code blockedSenderIsRejectedOverHttpButCanStillReadHistory}, which
 * confirms blocking gates sending only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DirectChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;
    @Autowired
    private DirectThreadRepository threadRepository;
    @Autowired
    private DirectMessageRepository messageRepository;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;

    private String aliceJwt;
    private String bobJwt;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        threadRepository.deleteAll();
        blockRepository.deleteAll();
        friendshipRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        aliceJwt = registerVerifyAndLogin("dmhttp-alice@example.com", "Alice");
        bobJwt = registerVerifyAndLogin("dmhttp-bob@example.com", "Bob");
        aliceId = userRepository.findByEmail("dmhttp-alice@example.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("dmhttp-bob@example.com").orElseThrow().getId();
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

    @Test
    void noFriendAddRequiredToSendOverHttp() throws Exception {
        // Neither alice nor bob has added the other as a friend.
        mockMvc.perform(post("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("hi bob"));

        mockMvc.perform(get("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("hi bob"));
    }

    @Test
    void blockedSenderIsRejectedOverHttpButCanStillReadHistory() throws Exception {
        mockMvc.perform(post("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"before block\"}"))
                .andExpect(status().isOk());

        // Bob blocks Alice.
        mockMvc.perform(post("/api/blocks/" + aliceId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNoContent());

        // Alice can no longer send to Bob.
        mockMvc.perform(post("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"cannot send\"}"))
                .andExpect(status().isForbidden());

        // But Alice can still read the existing history (viewing is never block-gated).
        mockMvc.perform(get("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("before block"));
    }

    @Test
    void blockedSenderCannotMessageEvenAfterAddingTheBlockerAsAFriendFullHttpScenario() throws Exception {
        // Alice adds Bob as a friend over real HTTP.
        mockMvc.perform(post("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(bobId));

        // Bob blocks Alice over real HTTP.
        mockMvc.perform(post("/api/blocks/" + aliceId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNoContent());

        // Alice -- despite having added Bob as a friend -- cannot send Bob a
        // direct message, because Bob has blocked her. This is the full
        // end-to-end combination the spec's corrected Resolved Questions
        // item 2/3 calls out: a prior friend-add has no bearing on a block,
        // exercised here over real HTTP (friend-add endpoint, block
        // endpoint, and DM-send endpoint all chained together), not just at
        // the service layer.
        mockMvc.perform(post("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"still blocked despite friend-add\"}"))
                .andExpect(status().isForbidden());

        // The friend-add itself persists unchanged underneath the block.
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(bobId));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/dm/" + bobId + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postingAnAttachmentFieldIsSilentlyIgnoredNotStoredOrEchoed() throws Exception {
        mockMvc.perform(post("/api/dm/" + bobId + "/messages")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"text only\",\"attachmentUrl\":\"http://example.com/file.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("text only"))
                .andExpect(jsonPath("$.attachmentUrl").doesNotExist());
    }
}
