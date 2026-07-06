package com.imin.backend.routing;

import com.imin.backend.routing.dto.RouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-side proxy for turn-by-turn routing (spec.md Maps/Routing,
 * design.md §6a): the frontend's map/routing UI ({@code RoutingControl},
 * a native react-leaflet component rendering this endpoint's
 * {@code coordinates}/{@code steps} directly — not the leaflet-routing-machine
 * plugin) calls this endpoint instead of calling the routing provider
 * (OpenRouteService) directly, so the provider's API key never ships in the
 * public frontend bundle. Authenticated like every other
 * non-{@code /api/auth/**} endpoint in this codebase ({@code SecurityConfig}'s
 * default {@code anyRequest().authenticated()} — no new public route added).
 */
@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
public class RoutingController {

    private static final String DEFAULT_PROFILE = "driving-car";
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private final RoutingService routingService;

    /**
     * Turn-by-turn directions between two coordinates.
     *
     * @param startLat  start latitude
     * @param startLng  start longitude
     * @param endLat    end latitude
     * @param endLng    end longitude
     * @param profile   routing profile (e.g. {@code driving-car},
     *                  {@code foot-walking}); defaults to {@code driving-car}
     *                  if omitted
     */
    @GetMapping("/directions")
    public ResponseEntity<RouteResponse> getDirections(@RequestParam double startLat,
                                                         @RequestParam double startLng,
                                                         @RequestParam double endLat,
                                                         @RequestParam double endLng,
                                                         @RequestParam(required = false) String profile) {
        requireValidLatitude(startLat, "startLat");
        requireValidLongitude(startLng, "startLng");
        requireValidLatitude(endLat, "endLat");
        requireValidLongitude(endLng, "endLng");
        try {
            RouteResponse route = routingService.getDirections(
                    startLat, startLng, endLat, endLng,
                    profile == null || profile.isBlank() ? DEFAULT_PROFILE : profile
            );
            return ResponseEntity.ok(route);
        } catch (RoutingException e) {
            // RoutingException messages are already client-safe (see RoutingService
            // javadoc) — never the raw provider error body or the API key.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private static void requireValidLatitude(double latitude, String paramName) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    paramName + " must be between " + MIN_LATITUDE + " and " + MAX_LATITUDE + " degrees, got " + latitude);
        }
    }

    private static void requireValidLongitude(double longitude, String paramName) {
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    paramName + " must be between " + MIN_LONGITUDE + " and " + MAX_LONGITUDE + " degrees, got " + longitude);
        }
    }
}
