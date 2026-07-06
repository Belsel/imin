package com.imin.backend.routing;

/**
 * Thrown by {@link RoutingService} implementations when a turn-by-turn
 * routing request cannot be fulfilled (bad/missing API key, rate limit,
 * network error, malformed provider response, or the provider rejecting the
 * request). The message is always a clean, client-safe description — never
 * the raw provider response body, and never an API key — since
 * {@link RoutingController} returns this message (or a generic fallback)
 * directly to the frontend.
 */
public class RoutingException extends RuntimeException {

    public RoutingException(String message) {
        super(message);
    }

    public RoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
