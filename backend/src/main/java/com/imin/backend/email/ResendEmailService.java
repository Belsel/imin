package com.imin.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends transactional email via Resend's REST API
 * (https://resend.com/docs/api-reference/emails/send-email).
 *
 * Delivery failures (missing/invalid API key, network error, non-2xx
 * response) are logged and swallowed rather than propagated — callers such
 * as registration must succeed regardless of whether the verification email
 * actually went out.
 */
@Service
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${email.from-address:}") String fromAddress) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.restClient = RestClient.builder().baseUrl(RESEND_API_URL).build();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY is not configured; skipping email send to {} (subject: {})", to, subject);
            return;
        }
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "from", fromAddress,
                            "to", new String[]{to},
                            "subject", subject,
                            "html", body
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send email to {} via Resend: {}", to, e.getMessage(), e);
        }
    }
}
