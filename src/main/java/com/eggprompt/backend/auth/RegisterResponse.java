package com.eggprompt.backend.auth;

public record RegisterResponse(
        String message,
        UserInfo user
) {

    public record UserInfo(
            Integer userId,
            String username,
            String email,
            String userRole
    ) {}
}