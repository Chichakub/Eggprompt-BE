package com.eggprompt.backend.health;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/health-cases", "/health-cases"})
@RequiredArgsConstructor
public class HealthCaseController {
    private final HealthCaseService service;

    public enum Severity { LOW, MEDIUM, HIGH }
    public enum Status {
        REPORTED, ASSESSED, ISOLATED, TREATING, FOLLOW_UP, RECOVERED, CLOSED;

        List<Status> allowedStatuses() {
            return switch (this) {
                case REPORTED -> List.of(REPORTED, ASSESSED, ISOLATED);
                case ASSESSED -> List.of(ASSESSED, ISOLATED, TREATING, FOLLOW_UP, RECOVERED);
                case ISOLATED -> List.of(ISOLATED, ASSESSED, TREATING, FOLLOW_UP, RECOVERED);
                case TREATING -> List.of(TREATING, FOLLOW_UP, RECOVERED);
                case FOLLOW_UP -> List.of(FOLLOW_UP, TREATING, RECOVERED);
                case RECOVERED -> List.of(RECOVERED, CLOSED);
                case CLOSED -> List.of();
            };
        }

        boolean allowsRecoveryAfterRecording() {
            return this == ASSESSED || this == TREATING;
        }
    }
    public record Create(@NotNull @Positive Integer flockId, @NotBlank @Size(max = 255) String cause,
                         @NotNull @Positive Integer affectedCount, @NotNull Severity severity,
                         @Size(max = 2000) String note) {}
    public record Update(@NotNull Status expectedStatus, @NotNull UUID expectedHistoryId, @NotNull Status status,
                         @Size(max = 2000) String note, @Valid Outcomes outcomes) {}
    public record Outcomes(@NotNull @PositiveOrZero Integer recovered, @NotNull @PositiveOrZero Integer dead,
                           @NotNull @PositiveOrZero Integer culled) {}

    @GetMapping
    public List<HealthCaseService.Summary> list(Authentication auth,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String scope) {
        return service.list(auth.getName(), scope);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HealthCaseService.Detail create(Authentication auth, @Valid @RequestBody Create request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String scope) {
        return service.create(auth.getName(), scope, request);
    }

    @GetMapping("/{id}")
    public HealthCaseService.Detail detail(Authentication auth, @PathVariable UUID id,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String scope) {
        return service.detail(auth.getName(), scope, id);
    }

    @PostMapping("/{id}/history")
    public HealthCaseService.Detail update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Update request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String scope) {
        return service.update(auth.getName(), scope, id, request);
    }
}
