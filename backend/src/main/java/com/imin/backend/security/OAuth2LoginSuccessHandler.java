package com.imin.backend.security;

import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setDisplayName(name);
            created.setProvider(AuthProvider.GOOGLE);
            created.setEmailVerified(true);
            return userRepository.save(created);
        });

        // An existing LOCAL account with the same email signing in via Google
        // is treated as completing verification: Google has independently
        // proven control of this email address, which is the same proof the
        // LOCAL verification-email flow exists to establish. We link the
        // account to GOOGLE and mark it verified rather than leaving an
        // unverified LOCAL row in place, which would otherwise let the
        // success handler silently bypass AuthService.login()'s
        // "LOCAL + unverified" rejection (see spec.md Users section).
        if (user.getProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            user.setProvider(AuthProvider.GOOGLE);
            user.setEmailVerified(true);
            user = userRepository.save(user);
        }

        String token = jwtService.generateToken(user.getEmail(), Map.of(
                "uid", user.getId(),
                "name", user.getDisplayName()
        ));

        response.sendRedirect(frontendUrl + "/oauth2/callback?token=" + token);
    }
}
