package com.imin.backend.routing.dto;

import java.util.List;

/**
 * Normalized turn-by-turn route, returned by
 * {@code GET /api/routing/directions}. This is a thin subset of whatever the
 * backing routing provider (OpenRouteService) returns — see design.md §6a —
 * deliberately reshaped so the frontend never needs to know the provider's
 * own response shape, and so nothing provider-internal (e.g. raw error
 * bodies) can leak through.
 *
 * <p>{@code coordinates} is the route geometry as a list of
 * {@code [lat, lng]} pairs, in Leaflet's expected order (note: GeoJSON, which
 * OpenRouteService returns, orders this {@code [lng, lat]} — already flipped
 * by the time it reaches this DTO), ready to hand directly to a Leaflet
 * polyline/Routing Machine control.
 */
public record RouteResponse(
        double distanceMeters,
        double durationSeconds,
        List<double[]> coordinates,
        List<RouteStep> steps
) {}
