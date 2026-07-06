package com.imin.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that turns every exception reaching the
 * `DispatcherServlet` into a clean, consistent JSON error body — written
 * directly via {@link ResponseEntity}, never via
 * {@code HttpServletResponse#sendError}.
 *
 * <p>Why this matters beyond tidiness: in the real (non-MockMvc) servlet
 * container, {@code sendError()} makes Tomcat internally forward the request
 * to {@code /error}, which re-enters the entire Spring Security filter
 * chain as a brand-new request. On a {@code permitAll()} path (e.g.
 * {@code /api/auth/**}), that forwarded request carries no credentials, so
 * the security chain treats it as an unauthenticated hit on a request it
 * doesn't recognize as public and overwrites the real status with a bare
 * {@code 401} and empty body. On authenticated paths the original status
 * code happens to survive, but the body is always replaced by Spring Boot's
 * generic default error payload ({@code timestamp/status/error/path}), which
 * carries no useful message. {@code MockMvc} never exhibits either symptom
 * because it does not run a real container and never performs this forward.
 *
 * <p>Returning a {@link ResponseEntity} from an {@code @ExceptionHandler}
 * resolves the exception via {@code ExceptionHandlerExceptionResolver}
 * (tried before {@code ResponseStatusExceptionResolver}/
 * {@code DefaultHandlerExceptionResolver} in the resolver chain) entirely
 * inside {@code DispatcherServlet}: the status is set with
 * {@code response.setStatus(...)} and the body is written by a message
 * converter. Because {@code sendError()} is never called, Tomcat never
 * forwards to {@code /error} and the security chain is never re-entered —
 * this fixes both the status-code corruption on {@code permitAll()} paths
 * and the lost-message problem everywhere else, for the same reason.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} (rather than adding a
 * blanket {@code @ExceptionHandler(Exception.class)}) so that the framework's
 * own well-known MVC exceptions — {@code MethodArgumentNotValidException},
 * {@code MissingServletRequestParameterException},
 * {@code MethodArgumentTypeMismatchException},
 * {@code HttpRequestMethodNotSupportedException},
 * {@code NoResourceFoundException}, etc. — keep being resolved by Spring's
 * own correct, already-tested logic (still inside
 * {@code ExceptionHandlerExceptionResolver}, so still {@code sendError()}-free)
 * instead of being shadowed by an over-broad catch-all here.
 *
 * <p>{@link ResponseStatusException} is this codebase's universal
 * error-throwing mechanism ({@code AuthService}, {@code GroupService},
 * {@code ActivityService}, {@code DirectChatService}, {@code SocialService},
 * {@code RoutingController}, etc.) and is handled explicitly to guarantee the
 * {@code {status, error, message}} shape below rather than the default
 * {@code ProblemDetail} shape. {@link BadCredentialsException} is also
 * handled explicitly because {@code AuthService.login} throws it directly
 * from a normal {@code @Service} call path (not from within the Spring
 * Security filter chain itself), so Spring Security's
 * {@code AuthenticationEntryPoint} never sees it and it would otherwise fall
 * through to the generic 500 fallback. Any other, truly unanticipated
 * exception still gets a clean JSON body instead of the container default,
 * with the real exception logged server-side (never echoed to the client)
 * for diagnosis.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : status.toString();
        return body(status, message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception reached ApiExceptionHandler", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * {@code ResponseEntityExceptionHandler}'s own handlers for the standard
     * MVC exceptions (validation, missing/mistyped params, unsupported
     * method, no handler found, etc.) all funnel through this method to
     * build their response body. Overridden so those exceptions get this
     * class's {@code {timestamp, status, error, message}} shape too, instead
     * of the default {@code ProblemDetail} shape — keeping every error
     * response in this app consistent — while still relying on the
     * superclass to pick the correct status code per exception type.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object bodyArg, HttpHeaders headers,
                                                               HttpStatusCode statusCode, WebRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : statusCode.toString();
        Map<String, Object> payload = errorBody(statusCode, message);
        return ResponseEntity.status(statusCode).headers(headers).body(payload);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(errorBody(status, message));
    }

    private static Map<String, Object> errorBody(HttpStatusCode status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        HttpStatus resolved = HttpStatus.resolve(status.value());
        payload.put("error", resolved != null ? resolved.getReasonPhrase() : status.toString());
        payload.put("message", message);
        return payload;
    }
}
