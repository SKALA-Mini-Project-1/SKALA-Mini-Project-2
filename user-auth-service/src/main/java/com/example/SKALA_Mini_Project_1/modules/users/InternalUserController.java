package com.example.SKALA_Mini_Project_1.modules.users;

import com.example.SKALA_Mini_Project_1.modules.users.dto.InternalUserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final InternalApiGuard internalApiGuard;
    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<InternalUserProfileResponse> getUserProfile(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable Long userId
    ) {
        try {
            internalApiGuard.validate(apiKey);
            User user = userService.getUserById(userId);
            return ResponseEntity.ok(new InternalUserProfileResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getPhone()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
