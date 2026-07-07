package com.imin.backend.user;

import com.imin.backend.auth.AuthService;
import com.imin.backend.auth.dto.AuthResponse;
import com.imin.backend.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DemoUserSeeder} — covers specs/try-demo-account/spec.md
 * Requirement 1 ("Provisioning (seeding)") and its "Seeding" acceptance
 * criteria: a cold startup creates exactly one correctly-shaped demo
 * {@link User} row, restarting never duplicates it or resets any field an
 * operator may have since changed (matches {@link
 * com.imin.backend.category.CategorySeeder}'s existing
 * check-then-insert-if-absent contract), and the seeded row's {@code
 * emailVerified = true} field means logging in with the demo credentials
 * never hits {@link AuthService#login}'s "email not verified" 403 branch.
 *
 * <p>Invokes {@link DemoUserSeeder#run} directly (rather than restarting the
 * whole Spring context, which isn't supported mid-test) — this exercises
 * exactly the same idempotency check ({@code userRepository.existsByEmail})
 * a real second boot would, deterministically, rather than depending on real
 * application-startup ordering relative to other test classes sharing this
 * test JVM's single named H2 instance (some of which, e.g. {@code
 * AuthFlowIntegrationTest}, wipe all users outside a transaction).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoUserSeederTest {

    @Autowired
    private DemoUserSeeder demoUserSeeder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;

    @Value("${demo.account.email}")
    private String demoEmail;
    @Value("${demo.account.password}")
    private String demoPassword;
    @Value("${demo.account.display-name}")
    private String demoDisplayName;

    @BeforeEach
    void cleanState() {
        // Simulates "no existing demo user row" -- a true cold start.
        userRepository.deleteAll();
    }

    @Test
    void coldStartupSeedsExactlyOneCorrectlyShapedDemoUser() {
        demoUserSeeder.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(1);

        User demoUser = userRepository.findFirstByDemoAccountTrue().orElseThrow();
        assertThat(demoUser.getEmail()).isEqualTo(demoEmail);
        assertThat(demoUser.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(demoUser.isEmailVerified()).isTrue();
        assertThat(demoUser.isDemoAccount()).isTrue();
        assertThat(demoUser.getPasswordHash()).isNotNull();
        assertThat(passwordEncoder.matches(demoPassword, demoUser.getPasswordHash())).isTrue();
        assertThat(demoUser.getDisplayName()).isEqualTo(demoDisplayName);
    }

    @Test
    void rerunningSeederDoesNotCreateASecondRowOrResetAnyField() {
        demoUserSeeder.run(new DefaultApplicationArguments()); // "first boot"

        User seeded = userRepository.findFirstByDemoAccountTrue().orElseThrow();
        // Simulate a manual DB fix / drift after first boot, per spec.md's
        // "manual DB fixes (e.g. after some drift) aren't silently undone on
        // the next deploy" requirement.
        seeded.setDisplayName("Manually Renamed Demo");
        userRepository.save(seeded);

        demoUserSeeder.run(new DefaultApplicationArguments()); // "restart"

        assertThat(userRepository.count()).isEqualTo(1);
        User afterRestart = userRepository.findFirstByDemoAccountTrue().orElseThrow();
        assertThat(afterRestart.getId()).isEqualTo(seeded.getId());
        assertThat(afterRestart.getDisplayName()).isEqualTo("Manually Renamed Demo");
    }

    @Test
    void loggingInWithDemoCredentialsNeverHitsEmailNotVerified403() {
        demoUserSeeder.run(new DefaultApplicationArguments());

        // If emailVerified were ever false for this LOCAL-provider account,
        // AuthService.login would throw a 403 ResponseStatusException here
        // instead of returning normally.
        AuthResponse response = authService.login(new LoginRequest(demoEmail, demoPassword));

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
