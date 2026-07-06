package com.imin.backend.security;

import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Touches {@link User#getLastSeenAt()} on every successfully authenticated
 * request. Runs after Spring Security's JWT resource-server filter has
 * already populated the {@link SecurityContextHolder}, so it only updates
 * users who presented a valid bearer token. This is the cheapest place to
 * capture "online" without a dedicated heartbeat endpoint, and feeds the
 * group admin-succession "online within 7 days" rule (a later slice).
 */
@Component
@RequiredArgsConstructor
public class LastSeenFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getSubject();
            if (email != null) {
                userRepository.findByEmail(email).ifPresent(user -> {
                    user.setLastSeenAt(Instant.now());
                    userRepository.save(user);
                });
            }
        }
    }
}
