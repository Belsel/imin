package com.imin.backend.user;

import com.imin.backend.user.dto.ProfileResponse;
import com.imin.backend.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public ProfileResponse getProfile(String email) {
        User user = findByEmail(email);
        return ProfileResponse.from(user);
    }

    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmail(email);

        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name cannot be blank");
            }
            user.setDisplayName(request.displayName());
        }

        if (request.bio() != null) {
            user.setBio(request.bio().isEmpty() ? null : request.bio());
        }

        userRepository.save(user);
        return ProfileResponse.from(user);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
