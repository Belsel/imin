package com.imin.backend.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for {@link OpenRouteServiceRoutingService} — no Spring
 * context, no live network call, and (deliberately) no real
 * {@code ORS_API_KEY}, since none is available in this sandbox. Confirms the
 * "missing key" failure path specifically, since that's the one path that's
 * fully exercisable without live ORS access: it never attempts a network
 * call at all, and its {@link RoutingException} message is a clean, generic
 * string that does not contain the (blank) key or any provider-internal
 * detail.
 *
 * <p>The "ORS reachable but rejects/errors" and "successful response parsed
 * into {@code RouteResponse}" paths are exercised indirectly via
 * {@code RoutingControllerTest}, which mocks the {@link RoutingService} seam
 * itself rather than this concrete implementation — consistent with how
 * {@code ResendEmailService}'s actual HTTP-calling logic is not
 * network-tested either (see spec.md Slice 1 Verification notes).
 */
class OpenRouteServiceRoutingServiceTest {

    @Test
    void aBlankApiKeyFailsFastWithoutAttemptingANetworkCallAndNeverLeaksTheKey() {
        OpenRouteServiceRoutingService service = new OpenRouteServiceRoutingService("");

        assertThatThrownBy(() -> service.getDirections(51.5, -0.1, 51.6, -0.2, "driving-car"))
                .isInstanceOf(RoutingException.class)
                .hasMessage("Routing is not currently available")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("ORS_API_KEY"));
    }

    @Test
    void aNullApiKeyAlsoFailsFastWithTheSameCleanMessage() {
        OpenRouteServiceRoutingService service = new OpenRouteServiceRoutingService(null);

        assertThatThrownBy(() -> service.getDirections(51.5, -0.1, 51.6, -0.2, "driving-car"))
                .isInstanceOf(RoutingException.class)
                .hasMessage("Routing is not currently available");
    }
}
