package com.imin.backend.category;

import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.category.dto.UpdateCategoryPreferencesRequest;
import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers spec.md acceptance criteria:
 * - The fixed category taxonomy is seeded and readable (CategorySeeder runs
 *   at application startup, see GroupServiceTest's reliance on the same
 *   seeded data).
 * - A user can select and update one or more group category preferences
 *   from the fixed taxonomy.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private GroupCategoryRepository groupCategoryRepository;
    @Autowired
    private UserCategoryPreferenceRepository userCategoryPreferenceRepository;
    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        userCategoryPreferenceRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("prefs@example.com");
        user.setPasswordHash("hash");
        user.setDisplayName("Prefs User");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Test
    void seededCategoriesAreListable() {
        List<CategoryResponse> categories = categoryService.listCategories();

        assertThat(categories).isNotEmpty();
        assertThat(categories).extracting(CategoryResponse::name).contains("Sports", "Tech", "Music");
    }

    @Test
    void userCanSetAndReplaceCategoryPreferences() {
        List<Long> firstTwoIds = groupCategoryRepository.findAll().stream().limit(2).map(GroupCategory::getId).toList();

        List<CategoryResponse> updated = categoryService.updatePreferences(user.getEmail(),
                new UpdateCategoryPreferencesRequest(firstTwoIds));

        assertThat(updated).hasSize(2);
        List<CategoryResponse> fetched = categoryService.getPreferences(user.getEmail());
        assertThat(fetched).extracting(CategoryResponse::id).containsExactlyInAnyOrderElementsOf(firstTwoIds);

        // Replacing with a single different category fully replaces the prior set.
        Long thirdId = groupCategoryRepository.findAll().get(2).getId();
        categoryService.updatePreferences(user.getEmail(), new UpdateCategoryPreferencesRequest(List.of(thirdId)));

        List<CategoryResponse> replaced = categoryService.getPreferences(user.getEmail());
        assertThat(replaced).hasSize(1);
        assertThat(replaced.get(0).id()).isEqualTo(thirdId);
    }

    @Test
    void updatePreferencesRejectsUnknownCategoryId() {
        assertThatThrownBy(() -> categoryService.updatePreferences(user.getEmail(),
                new UpdateCategoryPreferencesRequest(List.of(-999L))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void emptyPreferencesListClearsAllPreferences() {
        Long categoryId = groupCategoryRepository.findAll().get(0).getId();
        categoryService.updatePreferences(user.getEmail(), new UpdateCategoryPreferencesRequest(List.of(categoryId)));

        categoryService.updatePreferences(user.getEmail(), new UpdateCategoryPreferencesRequest(List.of()));

        assertThat(categoryService.getPreferences(user.getEmail())).isEmpty();
    }
}
