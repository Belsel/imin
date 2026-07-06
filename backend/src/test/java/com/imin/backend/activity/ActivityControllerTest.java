package com.imin.backend.activity;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for {@code ActivityController}, exercising the real
 * security filter chain (JWT bearer auth) plus bean validation, which the
 * service-level {@link ActivityServiceTest} cannot reach. Confirms: a member
 * can create/list/get, an owner can edit, a non-owner/non-admin is rejected,
 * and no recurrence/RSVP field is accepted or echoed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityControllerTest {

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
    private ActivityRepository activityRepository;

    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() throws Exception {
        activityRepository.deleteAll();
        banRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        aliceJwt = registerVerifyAndLogin("activity-alice@example.com", "Alice");
        bobJwt = registerVerifyAndLogin("activity-bob@example.com", "Bob");
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
                        .content("{\"name\":\"HTTP Activity Group\",\"description\":\"d\",\"latitude\":1.0,\"longitude\":1.0,\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void joinGroupViaApi(Long groupId, String jwt) throws Exception {
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void memberCanCreateListAndGetActivityOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        String createBody = mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trail Run\",\"description\":\"Easy 5k\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trail Run"))
                .andReturn().getResponse().getContentAsString();
        Long activityId = objectMapper.readTree(createBody).get("id").asLong();

        // The creator (alice) must be recorded as the activity's owner in
        // the real create-response body, not just asserted at the service
        // layer -- confirms ownerId is wired to the actual creator id, not
        // some other/default field, end to end over HTTP.
        String aliceId = objectMapper.readTree(mockMvc.perform(get("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0).get("userId").asText();
        org.junit.jupiter.api.Assertions.assertEquals(aliceId,
                objectMapper.readTree(createBody).get("ownerId").asText());

        mockMvc.perform(get("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Trail Run"))
                .andExpect(jsonPath("$[0].ownerId").value(Integer.parseInt(aliceId)));

        mockMvc.perform(get("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activityId))
                .andExpect(jsonPath("$.ownerId").value(Integer.parseInt(aliceId)));
    }

    @Test
    void editedActivityIsPersistedAndVisibleOnSubsequentGetOverHttp() throws Exception {
        // Confirms the PATCH's effect is durable across a separate later
        // GET, not just reflected in the PATCH call's own 200 response body.
        Long groupId = createGroupViaApi(aliceJwt);

        String createBody = mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Original\",\"description\":\"orig\",\"scheduledTime\":\"2026-07-01T10:00:00Z\",\"latitude\":1.0,\"longitude\":2.0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long activityId = objectMapper.readTree(createBody).get("id").asLong();

        mockMvc.perform(patch("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"description\":\"updated\",\"scheduledTime\":\"2026-07-05T10:00:00Z\",\"latitude\":9.0,\"longitude\":8.0}"))
                .andExpect(status().isOk());

        // Separate GET call, simulating a fresh page load/reload.
        mockMvc.perform(get("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.description").value("updated"))
                .andExpect(jsonPath("$.latitude").value(9.0))
                .andExpect(jsonPath("$.longitude").value(8.0));

        mockMvc.perform(get("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Renamed"));
    }

    @Test
    void activityCanBeCreatedWithoutLocationOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Picnic\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist());
    }

    @Test
    void ownerCanEditButNonOwnerNonAdminCannot() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);
        joinGroupViaApi(groupId, bobJwt);

        String createBody = mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob's Activity\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long activityId = objectMapper.readTree(createBody).get("id").asLong();

        // Owner (bob) can edit.
        mockMvc.perform(patch("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob Edited\",\"scheduledTime\":\"2026-07-02T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob Edited"));

        // Admin (alice, group creator) can also edit/delete bob's activity.
        mockMvc.perform(patch("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Edited\",\"scheduledTime\":\"2026-07-03T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Edited"));

        mockMvc.perform(delete("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonOwnerNonAdminMemberCannotEditOrDeleteOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);
        joinGroupViaApi(groupId, bobJwt);
        String carolJwt = registerVerifyAndLogin("activity-carol@example.com", "Carol");
        joinGroupViaApi(groupId, carolJwt);

        String createBody = mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob's Activity\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long activityId = objectMapper.readTree(createBody).get("id").asLong();

        mockMvc.perform(patch("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + carolJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonMemberIsRejectedOverHttp() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(get("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaking In\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void trueNonMemberCannotEditOrDeleteActivityOverHttp() throws Exception {
        // Bob owns the activity; carol has never joined this group at all
        // (true non-member, not banned, not a current-but-uninvolved
        // member) -- distinct from nonOwnerNonAdminMemberCannotEditOrDeleteOverHttp
        // above, which covers a *current member* who isn't owner/admin.
        // Closes the reviewer-flagged nit.
        Long groupId = createGroupViaApi(aliceJwt);
        joinGroupViaApi(groupId, bobJwt);
        String carolJwt = registerVerifyAndLogin("activity-carol-outsider@example.com", "CarolOutsider");

        String createBody = mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + bobJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob's Activity\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long activityId = objectMapper.readTree(createBody).get("id").asLong();

        mockMvc.perform(patch("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + carolJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isForbidden());

        // Confirm the activity survived the rejected attempts unchanged.
        mockMvc.perform(get("/api/groups/" + groupId + "/activities/" + activityId)
                        .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob's Activity"));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"scheduledTime\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noRecurrenceOrRsvpFieldIsAcceptedOrEchoed() throws Exception {
        // CreateActivityRequest/ActivityResponse have no recurrence/RSVP field
        // at all -- Jackson silently ignores unknown extra properties, so
        // submitting one neither errors nor results in such data being
        // persisted/returned.
        Long groupId = createGroupViaApi(aliceJwt);

        mockMvc.perform(post("/api/groups/" + groupId + "/activities")
                        .header("Authorization", "Bearer " + aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Recurrence\",\"scheduledTime\":\"2026-07-01T10:00:00Z\","
                                + "\"recurrence\":\"WEEKLY\",\"rsvpStatus\":\"GOING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrence").doesNotExist())
                .andExpect(jsonPath("$.rsvpStatus").doesNotExist());
    }
}
