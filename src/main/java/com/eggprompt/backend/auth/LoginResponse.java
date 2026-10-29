package com.eggprompt.backend.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(Integer userId, String username, String role, String farmId) {}
}