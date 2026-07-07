package com.imin.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the fixed, shared "Try demo account" {@link User} row at startup (see
 * specs/try-demo-account/spec.md). Idempotent — only inserts the demo user if
 * no row with its email already exists, so this is safe to run on every boot
 * (no duplicate rows, no destructive reset of an already-seeded row), mirroring
 * {@link com.imin.backend.category.CategorySeeder}'s check-then-insert-if-absent
 * pattern exactly.
 */
@Component
@RequiredArgsConstructor
public class DemoUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${demo.account.email}")
    private String demoEmail;
    @Value("${demo.account.password}")
    private String demoPassword;
    @Value("${demo.account.display-name}")
    private String demoDisplayName;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(demoEmail)) {
            return; // already seeded — never overwrite (matches CategorySeeder)
        }
        User demoUser = new User();
        demoUser.setEmail(demoEmail);
        demoUser.setPasswordHash(passwordEncoder.encode(demoPassword));
        demoUser.setDisplayName(demoDisplayName);
        demoUser.setProvider(AuthProvider.LOCAL);
        demoUser.setEmailVerified(true);
        demoUser.setDemoAccount(true);
        userRepository.save(demoUser);
    }
}
