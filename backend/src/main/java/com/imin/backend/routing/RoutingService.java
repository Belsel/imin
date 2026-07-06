package com.imin.backend.routing;

import com.imin.backend.routing.dto.RouteResponse;

/**
 * Thin seam over whatever turn-by-turn routing provider computes directions
 * on our behalf. Kept as an interface — mirroring
 * {@link com.imin.backend.email.EmailService}'s role for Resend — so
 * {@link RoutingController} can be exercised in tests without a live network
 * call to the routing provider, and so the provider is swappable.
 *
 * <p>Unlike {@code EmailService#send}, failures here are <b>not</b> swallowed:
 * a routing request that fails (bad key, rate limit, network error, provider
 * error) is something the caller (the frontend, via {@link RoutingController})
 * needs to know about, so implementations must throw {@link RoutingException}
 * rather than silently returning an empty/partial result. Implementations
 * must never let any upstream API key, raw provider error body, or other
 * provider-internal detail leak into the {@link RoutingException} message.
 */
public interface RoutingService {

    /**
     * Compute turn-by-turn directions from the start coordinate to the end
     * coordinate.
     *
     * @param startLat start latitude
     * @param startLng start longitude
     * @param endLat   end latitude
     * @param endLng   end longitude
     * @param profile  routing profile (e.g. {@code driving-car}, {@code foot-walking})
     * @throws RoutingException if the provider call fails for any reason
     */
    RouteResponse getDirections(double startLat, double startLng, double endLat, double endLng, String profile);
}
