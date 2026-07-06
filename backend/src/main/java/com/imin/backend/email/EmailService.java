package com.imin.backend.email;

/**
 * Thin seam over whatever transactional email provider sends mail on our
 * behalf. Kept as an interface so registration/login flows can be exercised
 * in tests without a live network call, and so the provider is swappable.
 */
public interface EmailService {

    /**
     * Send a single plain-text/HTML email. Implementations must not throw
     * for ordinary delivery failures (e.g. provider outage) — callers (e.g.
     * registration) must not be blocked by email delivery issues. Log and
     * swallow instead.
     */
    void send(String to, String subject, String body);
}
