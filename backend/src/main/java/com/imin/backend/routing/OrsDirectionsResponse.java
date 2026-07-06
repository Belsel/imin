package com.imin.backend.routing;

import java.util.List;

/**
 * Deserialization shape for OpenRouteService's
 * {@code POST /v2/directions/{profile}} response, requested with
 * {@code geometry_format: "geojson"} (see {@link OpenRouteServiceRoutingService}),
 * which makes {@code routes[].geometry} a GeoJSON {@code LineString} instead
 * of an encoded polyline — simpler to parse with no extra polyline-decoding
 * dependency. This is an internal parsing detail only; never returned
 * directly from {@code RoutingController} — see {@link OpenRouteServiceRoutingService#toRouteResponse}
 * for the normalization into the public {@link com.imin.backend.routing.dto.RouteResponse} shape.
 */
record OrsDirectionsResponse(List<OrsRoute> routes) {

    record OrsRoute(OrsSummary summary, OrsGeometry geometry, List<OrsSegment> segments) {}

    record OrsSummary(double distance, double duration) {}

    /** GeoJSON LineString: {@code coordinates} is a list of {@code [lng, lat]} pairs. */
    record OrsGeometry(String type, List<List<Double>> coordinates) {}

    record OrsSegment(double distance, double duration, List<OrsStep> steps) {}

    record OrsStep(double distance, double duration, String instruction) {}
}
