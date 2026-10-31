package com.eggprompt.backend.user;

import com.eggprompt.backend.auth.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // PUT /v1/users/me — แก้ไขข้อมูลส่วนตัวของผู้ใช้ที่ล็อกอินอยู่
    @PutMapping("/me")
    public UserProfileResponse updateMe(
            Authentication authentication,
            @Valid @RequestBody EditProfileRequest request
    ) {
        return userService.updateMyProfile(authentication.getName(), request);
    }
}