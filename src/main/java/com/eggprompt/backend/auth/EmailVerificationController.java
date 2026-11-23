package com.eggprompt.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService verification;

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.CREATED)
    public void verify(@Valid @RequestBody VerifyRequest request) {
        verification.verify(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendRequest request) {
        verification.resend(request.email());
    }

    public record VerifyRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
    public record ResendRequest(@NotBlank @Email @Size(max = 255) String email) {}
}
