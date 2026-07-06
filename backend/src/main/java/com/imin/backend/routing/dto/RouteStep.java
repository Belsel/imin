package com.imin.backend.routing.dto;

/**
 * One turn-by-turn instruction within a {@link RouteResponse}. Mirrors the
 * subset of OpenRouteService's {@code segments[].steps[]} shape that a
 * Leaflet Routing Machine instruction panel needs — distance/duration are in
 * meters/seconds, consistent with {@link RouteResponse}.
 */
public record RouteStep(
        String instruction,
        double distanceMeters,
        double durationSeconds
) {}
