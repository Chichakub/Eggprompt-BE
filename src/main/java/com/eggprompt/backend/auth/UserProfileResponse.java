package com.eggprompt.backend.auth;

public record UserProfileResponse(
        Integer userId,
        String username,
        String role,
        String farmId,
        String userCode,
        String fullName,
        String email,
        String phoneNumber,
        String position,
        String status         // "active" | "inactive"
) {}