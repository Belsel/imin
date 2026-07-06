package com.imin.backend.social;

import com.imin.backend.auth.EmailVerificationTokenRepository;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for {@code SocialController} (friends + blocks),
 * exercising the real security filter chain via {@code MockMvc}. The
 * existing {@code SocialServiceTest} only ever calls {@code SocialService}
 * directly, so none of its assertions actually prove the real
 * {@code GET /api/friends}/{@code GET /api/blocks} HTTP endpoints behave
 * correctly, or that they scope results to the calling JWT's own user only.
 * This file closes that gap: it confirms the unfriend-then-relist round trip
 * through the real {@code GET /api/friends} endpoint, that each direction's
 * listing is independent at the HTTP layer (not just the data layer), and
 * that both {@code GET /api/friends} and {@code GET /api/blocks} return only
 * the calling user's own relationships, never another user's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private BlockRepository blockRepository;

    private String aliceJwt;
    private String bobJwt;
    private String carolJwt;
    private Long aliceId;
    private Long bobId;
    private Long carolId;

    @BeforeEach
    void setUp() throws Exception {
        friendshipRepository.deleteAll();
        blockRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        aliceJwt = registerVerifyAndLogin("social-alice@example.com", "Alice");
        bobJwt = registerVerifyAndLogin("social-bob@example.com", "Bob");
        carolJwt = registerVerifyAndLogin("social-carol@example.com", "Carol");
        aliceId = userRepository.findByEmail("social-alice@example.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("social-bob@example.com").orElseThrow().getId();
        carolId = userRepository.findByEmail("social-carol@example.com").orElseThrow().getId();
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
    void addFriendOverHttpThenListFriendsShowsTheAdd() throws Exception {
        mockMvc.perform(post("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(bobId));
    }

    @Test
    void unfriendingOverHttpRemovesTheTargetFromTheCallersOwnFriendListing() throws Exception {
        // Alice adds Bob.
        mockMvc.perform(post("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Alice unfriends Bob via the real DELETE endpoint.
        mockMvc.perform(delete("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        // Alice's own GET /api/friends listing no longer includes Bob.
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unfriendingOneDirectionLeavesTheOtherDirectionsOwnFriendListingIntactOverHttp() throws Exception {
        // Alice adds Bob, and Bob separately adds Alice -- two independent edges.
        mockMvc.perform(post("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/friends/" + aliceId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNoContent());

        // Alice unfriends Bob (removes only Alice's own add of Bob).
        mockMvc.perform(delete("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        // Alice's own listing no longer includes Bob.
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Bob's own friend-add of Alice survives, fetched via Bob's own JWT
        // hitting the real GET /api/friends endpoint -- proving the two
        // directions are independent at the listing/HTTP level, not just at
        // the underlying data/repository level.
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(aliceId));
    }

    @Test
    void listFriendsOverHttpReturnsOnlyTheCallersOwnFriendsNotAnotherUsersFriends() throws Exception {
        // Alice adds Bob and Carol; Bob separately adds Carol.
        mockMvc.perform(post("/api/friends/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/friends/" + carolId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/friends/" + carolId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNoContent());

        // Alice's own listing shows exactly her two adds (Bob, Carol).
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Bob's own listing shows only his own add (Carol) -- not Alice's
        // relationships, even though Alice and Bob share a common target.
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(carolId));

        // Carol never added anyone -- her own listing is empty, even though
        // she is the target of two other users' adds.
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listBlocksOverHttpReturnsOnlyTheCallersOwnBlocksNotAnotherUsersBlocks() throws Exception {
        // Alice blocks Bob and Carol; Bob separately blocks Carol.
        mockMvc.perform(post("/api/blocks/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/blocks/" + carolId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/blocks/" + carolId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNoContent());

        // Alice's own listing shows exactly her two blocks.
        mockMvc.perform(get("/api/blocks")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Bob's own listing shows only his own block (Carol) -- not Alice's.
        mockMvc.perform(get("/api/blocks")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(carolId));

        // Carol never blocked anyone -- her own listing is empty, even though
        // she is the target of two other users' blocks.
        mockMvc.perform(get("/api/blocks")
                        .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unblockingOverHttpRemovesTheTargetFromTheCallersOwnBlockListing() throws Exception {
        mockMvc.perform(post("/api/blocks/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/blocks/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/blocks")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unauthenticatedRequestToFriendsOrBlocksIsRejected() throws Exception {
        mockMvc.perform(get("/api/friends"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isUnauthorized());
    }
}
