package com.imin.backend.category;

import com.imin.backend.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to the fixed, developer-curated category taxonomy. There
 * is deliberately no POST/PATCH/DELETE here — categories are seeded by
 * {@link CategorySeeder} and are not manageable via the API (per spec.md
 * Groups: "users and groups cannot create, rename, or otherwise manage
 * categories themselves").
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.listCategories());
    }
}
