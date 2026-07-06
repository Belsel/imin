package com.imin.backend.category;

import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.category.dto.UpdateCategoryPreferencesRequest;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final GroupCategoryRepository groupCategoryRepository;
    private final UserCategoryPreferenceRepository userCategoryPreferenceRepository;
    private final UserRepository userRepository;

    public List<CategoryResponse> listCategories() {
        return groupCategoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public List<CategoryResponse> getPreferences(String email) {
        User user = findUserByEmail(email);
        List<Long> categoryIds = userCategoryPreferenceRepository.findByUserId(user.getId()).stream()
                .map(UserCategoryPreference::getCategoryId)
                .toList();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return groupCategoryRepository.findAllById(categoryIds).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public List<CategoryResponse> updatePreferences(String email, UpdateCategoryPreferencesRequest request) {
        User user = findUserByEmail(email);
        List<Long> requestedIds = request.categoryIds().stream().distinct().toList();

        List<GroupCategory> categories = groupCategoryRepository.findAllById(requestedIds);
        if (categories.size() != requestedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more category ids do not exist");
        }

        userCategoryPreferenceRepository.deleteByUserId(user.getId());
        for (Long categoryId : requestedIds) {
            UserCategoryPreference preference = new UserCategoryPreference();
            preference.setUserId(user.getId());
            preference.setCategoryId(categoryId);
            userCategoryPreferenceRepository.save(preference);
        }

        return categories.stream().map(CategoryResponse::from).toList();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
