package com.eggprompt.backend.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@Profile("dev")
public class DevRoleAccessController {
    @GetMapping("/v1/users/access-check")
    public Map<String, String> check(Authentication authentication) {
        return Map.of("message", "Access granted", "username", authentication.getName());
    }
}
