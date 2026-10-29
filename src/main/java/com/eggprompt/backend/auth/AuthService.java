package com.eggprompt.backend.auth;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(request.username())
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                new LoginResponse.UserInfo(
                        user.getUserId(), user.getUsername(), user.getUserRole(), user.getFarmId())
        );
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}