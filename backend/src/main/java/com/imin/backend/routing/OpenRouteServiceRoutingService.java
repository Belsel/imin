package com.imin.backend.routing;

import com.imin.backend.routing.dto.RouteResponse;
import com.imin.backend.routing.dto.RouteStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Calls OpenRouteService's Directions API
 * (https://openrouteservice.org/dev/#/api-docs/v2/directions/{profile}/post)
 * server-side, via Spring's {@code RestClient} — mirroring
 * {@link com.imin.backend.email.ResendEmailService}'s pattern for calling an
 * external API with a config-driven key. See design.md §6a for why this call
 * happens server-side rather than directly from the browser: the
 * {@code ORS_API_KEY} must never ship in the public frontend bundle.
 *
 * <p>Unlike {@code ResendEmailService}, failures here are not swallowed —
 * see {@link RoutingService} javadoc — every failure mode (missing/invalid
 * key, network error, non-2xx response, unparseable body) is caught and
 * re-thrown as a {@link RoutingException} with a clean, generic message.
 * The {@code ORS_API_KEY} and the raw upstream response/error body are never
 * included in that message or logged at a level visible to clients — they
 * are only logged server-side at {@code ERROR}/{@code WARN} for operator
 * diagnosis.
 */
@Service
public class OpenRouteServiceRoutingService implements RoutingService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceRoutingService.class);
    private static final String ORS_BASE_URL = "https://api.openrouteservice.org/v2/directions";

    private final RestClient restClient;
    private final String apiKey;

    public OpenRouteServiceRoutingService(@Value("${ors.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl(ORS_BASE_URL).build();
    }

    @Override
    public RouteResponse getDirections(double startLat, double startLng, double endLat, double endLng, String profile) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("ORS_API_KEY is not configured; cannot fulfill routing request");
            throw new RoutingException("Routing is not currently available");
        }

        OrsDirectionsResponse response;
        try {
            response = restClient.post()
                    .uri("/{profile}", profile)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", apiKey)
                    .body(Map.of(
                            "coordinates", List.of(
                                    List.of(startLng, startLat),
                                    List.of(endLng, endLat)
                            ),
                            "geometry_format", "geojson"
                    ))
                    .retrieve()
                    .body(OrsDirectionsResponse.class);
        } catch (RestClientException e) {
            log.error("OpenRouteService directions request failed: {}", e.getMessage(), e);
            throw new RoutingException("Unable to compute a route right now. Please try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error calling OpenRouteService: {}", e.getMessage(), e);
            throw new RoutingException("Unable to compute a route right now. Please try again later.", e);
        }

        return toRouteResponse(response);
    }

    private RouteResponse toRouteResponse(OrsDirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.error("OpenRouteService returned no routes for the given coordinates");
            throw new RoutingException("No route could be found between those locations");
        }

        OrsDirectionsResponse.OrsRoute route = response.routes().get(0);

        List<double[]> coordinates = route.geometry() == null || route.geometry().coordinates() == null
                ? List.of()
                : route.geometry().coordinates().stream()
                        .map(pair -> new double[]{pair.get(1), pair.get(0)})
                        .toList();

        List<RouteStep> steps = route.segments() == null
                ? List.of()
                : route.segments().stream()
                        .flatMap(segment -> segment.steps() == null ? List.<OrsDirectionsResponse.OrsStep>of().stream() : segment.steps().stream())
                        .map(step -> new RouteStep(step.instruction(), step.distance(), step.duration()))
                        .toList();

        double distance = route.summary() == null ? 0.0 : route.summary().distance();
        double duration = route.summary() == null ? 0.0 : route.summary().duration();

        return new RouteResponse(distance, duration, coordinates, steps);
    }
}
