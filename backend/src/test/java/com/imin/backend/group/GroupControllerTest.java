package com.imin.backend.group;

import com.imin.backend.auth.EmailVerificationTokenRepository;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.category.GroupCategoryRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for {@code GroupController}, exercising the real
 * security filter chain (JWT bearer auth) on top of the already-thorough
 * service-level coverage in {@link GroupServiceTest}. Focuses on HTTP-layer
 * concerns this slice didn't yet have a dedicated test for: every route is
 * reachable at its documented path/method, request/response bodies actually
 * serialize/deserialize over real JSON, and the main auth-denial cases
 * (unauthenticated, non-member, non-admin, banned) surface the expected
 * status code through the full filter chain. Business-rule details (admin
 * succession math, recommendation ranking, etc.) are intentionally not
 * re-derived here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupControllerTest {

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
    private GroupCategoryLinkRepository categoryLinkRepository;
    @Autowired
    private GroupCategoryRepository groupCategoryRepository;

    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() throws Exception {
        categoryLinkRepository.deleteAll();
        banRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        aliceJwt = registerVerifyAndLogin("group-alice@example.com", "Alice");
        bobJwt = registerVerifyAndLogin("group-bob@example.com", "Bob");
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

    private Long createGroupViaApi(String creatorJwt, String name) throws Exception {
        String body = mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + creatorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"d\",\"latitude\":1.0,\"longitude\":1.0,\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createGroupOverHttpPersistsAndReturnsExpectedBody() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hikers\",\"description\":\"We hike\",\"latitude\":51.5,\"longitude\":-0.1,\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hikers"))
                .andExpect(jsonPath("$.latitude").value(51.5))
                .andExpect(jsonPath("$.longitude").value(-0.1))
                .andExpect(jsonPath("$.isAdmin").value(true))
                .andExpect(jsonPath("$.isMember").value(true))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void createGroupWithBlankNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"description\":\"d\",\"latitude\":1.0,\"longitude\":1.0,\"categoryIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGroupUnauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anon Group\",\"description\":\"d\",\"latitude\":1.0,\"longitude\":1.0,\"categoryIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupAfterCreationViaRealGetRoundTrip() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Round Trip Group");

        mockMvc.perform(get("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.name").value("Round Trip Group"));
    }

    @Test
    void getNonExistentGroupReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/groups/999999")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchGroupsFindsCreatedGroupByName() throws Exception {
        createGroupViaApi(aliceJwt, "Mountain Bikers HTTP");

        mockMvc.perform(get("/api/groups/search")
                        .param("q", "Mountain")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mountain Bikers HTTP"));
    }

    @Test
    void recommendationsEndpointReturnsOkWithRequiredLatLngParams() throws Exception {
        createGroupViaApi(aliceJwt, "Recommended Group");

        mockMvc.perform(get("/api/groups/recommendations")
                        .param("latitude", "1.0")
                        .param("longitude", "1.0")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());
    }

    @Test
    void joinGroupOverHttpAddsCallerAsNonAdminMember() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Joinable Group");

        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMember").value(true))
                .andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void listMembersIncludesJoinedUser() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Roster Group HTTP");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void nonMemberCannotListMembers() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Private Roster Group");

        mockMvc.perform(get("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaveGroupOverHttpRemovesMembership() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Leavable Group");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/me")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void nonAdminCannotUpdateGroup() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Protected Name Group");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked Name\",\"description\":\"d\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateGroupNameAndDescription() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Old Name Group");

        mockMvc.perform(patch("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name Group\",\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name Group"))
                .andExpect(jsonPath("$.description").value("updated"));
    }

    @Test
    void nonAdminCannotKickOrBanMember() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Kick Guard Group");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());
        Long bobId = userRepository.findByEmail("group-bob@example.com").orElseThrow().getId();

        // Bob (non-admin) tries to kick/ban himself -- both must be admin-only.
        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + bobId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/groups/" + groupId + "/bans/" + bobId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanBanThenUnbanMemberOverHttpAndListReflectsIt() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Ban Cycle Group");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());
        Long bobId = userRepository.findByEmail("group-bob@example.com").orElseThrow().getId();

        mockMvc.perform(post("/api/groups/" + groupId + "/bans/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + groupId + "/bans")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(bobId));

        // Banned user is rejected from rejoining over HTTP.
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());

        // Banned user cannot view the group either (404, per design.md no-confirm rule).
        mockMvc.perform(get("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/groups/" + groupId + "/bans/" + bobId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + groupId + "/bans")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Unbanned user can rejoin again.
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotListBans() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Ban List Guard Group");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/groups/" + groupId + "/bans")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void addAndRemoveCategoryOverHttpUpdatesGroupResponse() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Category Group HTTP");
        Long categoryId = groupCategoryRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/groups/" + groupId + "/categories")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1));

        mockMvc.perform(delete("/api/groups/" + groupId + "/categories/" + categoryId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void adminCanDeleteGroupOverHttpAndSubsequentGetIs404() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Doomed Group HTTP");

        mockMvc.perform(delete("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminCannotDeleteGroupOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt, "Undeletable By Bob Group");
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk());
    }
}
