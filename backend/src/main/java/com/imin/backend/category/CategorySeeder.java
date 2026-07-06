package com.imin.backend.category;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the fixed, developer-curated {@link GroupCategory} taxonomy at
 * startup. Idempotent — only inserts categories that don't already exist by
 * name, so this is safe to run on every boot (no duplicate rows, no
 * destructive reset of an already-seeded table). There is intentionally no
 * API path that lets a user or group add/rename/remove a category; this
 * seeder is the only writer of this table.
 */
@Component
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    private static final List<String> SEED_CATEGORIES = List.of(
            "Sports",
            "Outdoors & Hiking",
            "Board Games",
            "Video Games",
            "Books & Writing",
            "Music",
            "Food & Drink",
            "Tech",
            "Arts & Crafts",
            "Fitness",
            "Travel",
            "Social"
    );

    private final GroupCategoryRepository groupCategoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (String name : SEED_CATEGORIES) {
            if (!groupCategoryRepository.existsByName(name)) {
                GroupCategory category = new GroupCategory();
                category.setName(name);
                groupCategoryRepository.save(category);
            }
        }
    }
}
