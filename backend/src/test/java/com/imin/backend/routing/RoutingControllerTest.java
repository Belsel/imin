package com.imin.backend.routing;

import com.imin.backend.auth.EmailVerificationTokenRepository;
import com.imin.backend.auth.dto.LoginRequest;
import com.imin.backend.auth.dto.RegisterRequest;
import com.imin.backend.routing.dto.RouteResponse;
import com.imin.backend.routing.dto.RouteStep;
import com.imin.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP tests for {@code RoutingController}, exercising the real
 * security filter chain (an unauthenticated caller must be rejected, per
 * spec.md Maps/Routing + design.md §6a's "authenticated like every other
 * endpoint" posture) and the controller's error mapping. {@link RoutingService}
 * is mocked (the seam described in its own javadoc) so these tests never need
 * a live network call or a real {@code ORS_API_KEY} — mirroring how
 * {@code AuthServiceTest} mocks {@link com.imin.backend.email.EmailService}
 * rather than hitting Resend's live API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoutingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    @MockitoBean
    private RoutingService routingService;

    private String jwt;

    @BeforeEach
    void setUp() throws Exception {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("routing-caller@example.com", "password123", "Routing Caller"))))
                .andExpect(status().isOk());
        String token = verificationTokenRepository.findAll().get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token)).andExpect(status().isOk());

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("routing-caller@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        jwt = objectMapper.readTree(loginBody).get("token").asText();
    }

    @Test
    void anUnauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/routing/directions")
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAuthenticatedCallerReceivesTheNormalizedRoute() throws Exception {
        RouteResponse fakeRoute = new RouteResponse(
                1234.5,
                321.0,
                List.of(new double[]{51.5, -0.1}, new double[]{51.6, -0.2}),
                List.of(new RouteStep("Head north", 100.0, 20.0))
        );
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("driving-car")))
                .thenReturn(fakeRoute);

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(1234.5))
                .andExpect(jsonPath("$.durationSeconds").value(321.0))
                .andExpect(jsonPath("$.coordinates.length()").value(2))
                .andExpect(jsonPath("$.steps[0].instruction").value("Head north"));

        verify(routingService).getDirections(51.5, -0.1, 51.6, -0.2, "driving-car");
    }

    @Test
    void aProfileQueryParamIsPassedThroughToTheService() throws Exception {
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("foot-walking")))
                .thenReturn(new RouteResponse(10.0, 5.0, List.of(), List.of()));

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2")
                        .param("profile", "foot-walking"))
                .andExpect(status().isOk());

        verify(routingService).getDirections(51.5, -0.1, 51.6, -0.2, "foot-walking");
    }

    @Test
    void omittingProfileDefaultsToDrivingCar() throws Exception {
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("driving-car")))
                .thenReturn(new RouteResponse(10.0, 5.0, List.of(), List.of()));

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isOk());

        verify(routingService).getDirections(51.5, -0.1, 51.6, -0.2, "driving-car");
    }

    @Test
    void aRoutingServiceFailureIsReturnedAsACleanBadGatewayNotARawErrorOrCrash() throws Exception {
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenThrow(new RoutingException("Unable to compute a route right now. Please try again later."));

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isBadGateway())
                // The message is asserted on the JSON response body (written
                // directly by ApiExceptionHandler), not
                // MockHttpServletResponse#getErrorMessage(): the latter is
                // only ever populated by HttpServletResponse#sendError(),
                // which ApiExceptionHandler deliberately never calls (see its
                // javadoc) precisely so a real servlet container never
                // forwards to /error and corrupts/erases this message.
                .andExpect(jsonPath("$.message")
                        .value("Unable to compute a route right now. Please try again later."));
    }

    @Test
    void missingRequiredCoordinateParamsIsRejectedAsABadRequest() throws Exception {
        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericCoordinateParamIsRejectedAsABadRequest() throws Exception {
        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "not-a-number")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPopulatedResponseWithMultipleTurnByTurnStepsReturnsFullStepDataNotJustADistanceAndDuration() throws Exception {
        RouteResponse fakeRoute = new RouteResponse(
                5000.0,
                600.0,
                List.of(new double[]{51.5, -0.1}, new double[]{51.55, -0.15}, new double[]{51.6, -0.2}),
                List.of(
                        new RouteStep("Head north on Main St", 200.0, 30.0),
                        new RouteStep("Turn right onto High St", 1800.0, 240.0),
                        new RouteStep("Arrive at destination", 0.0, 0.0)
                )
        );
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("driving-car")))
                .thenReturn(fakeRoute);

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].instruction").value("Head north on Main St"))
                .andExpect(jsonPath("$.steps[0].distanceMeters").value(200.0))
                .andExpect(jsonPath("$.steps[0].durationSeconds").value(30.0))
                .andExpect(jsonPath("$.steps[1].instruction").value("Turn right onto High St"))
                .andExpect(jsonPath("$.steps[1].distanceMeters").value(1800.0))
                .andExpect(jsonPath("$.steps[1].durationSeconds").value(240.0))
                .andExpect(jsonPath("$.steps[2].instruction").value("Arrive at destination"))
                .andExpect(jsonPath("$.coordinates.length()").value(3));
    }

    /**
     * Latitude is range-validated at the proxy boundary before the request
     * ever reaches {@link RoutingService#getDirections} — valid range is
     * [-90, 90]. An out-of-range value (e.g. 999) must short-circuit with a
     * clean 400 rather than being passed through uncritically.
     */
    @Test
    void outOfRangeLatitudeIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "999")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isBadRequest());

        verify(routingService, never())
                .getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    /**
     * Same validation as above, for longitude (valid range is [-180, 180]).
     */
    @Test
    void outOfRangeLongitudeIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "500")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2"))
                .andExpect(status().isBadRequest());

        verify(routingService, never())
                .getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    /**
     * An unexpected/invalid {@code profile} value (e.g. neither
     * {@code driving-car} nor any other ORS-recognized profile) must not
     * crash the controller. Per design.md and the implementer's own notes,
     * {@code profile} has no allow-list — any string is forwarded as-is —
     * so an invalid value is expected to surface as a clean
     * {@link RoutingException}/502 from the routing service (simulating what
     * a real ORS rejection would look like), not an unhandled exception.
     */
    @Test
    void anUnexpectedProfileValueDoesNotCauseAnUnhandledExceptionAndStillMapsToACleanBadGateway() throws Exception {
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("not-a-real-profile")))
                .thenThrow(new RoutingException("Unable to compute a route right now. Please try again later."));

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2")
                        .param("profile", "not-a-real-profile"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("Unable to compute a route right now. Please try again later."));
    }

    /**
     * Same scenario as above, but for a blank/whitespace-only profile value
     * supplied explicitly (as opposed to omitted entirely, which already
     * defaults to driving-car per {@code omittingProfileDefaultsToDrivingCar}
     * above) — confirms the blank-check also covers an explicit blank string,
     * not just a wholly-absent parameter.
     */
    @Test
    void aBlankProfileQueryParamAlsoDefaultsToDrivingCarRatherThanCrashing() throws Exception {
        when(routingService.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("driving-car")))
                .thenReturn(new RouteResponse(10.0, 5.0, List.of(), List.of()));

        mockMvc.perform(get("/api/routing/directions")
                        .header("Authorization", "Bearer " + jwt)
                        .param("startLat", "51.5")
                        .param("startLng", "-0.1")
                        .param("endLat", "51.6")
                        .param("endLng", "-0.2")
                        .param("profile", "   "))
                .andExpect(status().isOk());

        verify(routingService).getDirections(51.5, -0.1, 51.6, -0.2, "driving-car");
    }
}
