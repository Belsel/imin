package com.imin.backend.category;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests confirming {@code CategoryController} only exposes a
 * read-only surface, per spec.md Groups: "Users and groups cannot create,
 * rename, or otherwise manage categories themselves." Uses a real,
 * authenticated caller (not just an unauthenticated request) so that a
 * rejected mutating request is confirmed to be a routing/handler-mapping
 * rejection (404/405, i.e. "this operation doesn't exist in this API") rather
 * than merely "you're not logged in" (401) -- the two would be
 * indistinguishable for an unauthenticated caller, since Spring Security's
 * filter chain runs before Spring MVC's handler-mapping check.
 *
 * {@code POST /api/categories} hits the same base path as the existing
 * {@code GET} mapping, so an unmapped verb on that path correctly 405s
 * (path matches, verb doesn't). {@code PUT}/{@code DELETE
 * /api/categories/{id}} target a path for which no mapping exists at all
 * (the controller has no path-variable route), so those correctly 404
 * (no handler matches the path itself) -- both outcomes equally confirm no
 * mutating category endpoint is reachable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    private String jwt;

    @BeforeEach
    void setUp() throws Exception {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("category-caller@example.com", "password123", "Category Caller"))))
                .andExpect(status().isOk());
        String token = verificationTokenRepository.findAll().get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token)).andExpect(status().isOk());

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("category-caller@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        jwt = objectMapper.readTree(loginBody).get("token").asText();
    }

    @Test
    void getCategoriesIsAvailableToAnyAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void noPostEndpointExistsToCreateACategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked Category\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void noPutEndpointExistsToRenameACategory() throws Exception {
        // No /api/categories/{id} route is mapped at all (not even GET), so
        // this 404s rather than 405s -- there is simply no per-category
        // path, mutating or otherwise.
        mockMvc.perform(put("/api/categories/1")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noDeleteEndpointExistsToRemoveACategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}
