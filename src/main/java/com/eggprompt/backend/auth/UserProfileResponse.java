package com.eggprompt.backend.auth;

public record UserProfileResponse(
        Integer userId,
        String username,
        String role,
        String farmId
) {}