package com.eggprompt.backend.health;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/health-logs", "/health-logs"})
@RequiredArgsConstructor
public class HealthLogController {
    private final HealthLogService service;

    @GetMapping("/loss-history")
    public List<HealthLogService.LossHistory> lossHistory(Authentication authentication,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        return service.lossHistory(authentication.getName(), farmScope);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HealthLogService.Result create(Authentication authentication, @Valid @RequestBody Request request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        return service.create(authentication.getName(), request, farmScope);
    }

    public record Request(
            @NotNull @Positive Integer flockId,
            @NotNull LocalDate recordDate,
            @NotNull @PositiveOrZero Integer liveHenCount,
            @PositiveOrZero Integer deadCount,
            @PositiveOrZero Integer cullCount,
            @PositiveOrZero Integer sickCount,
            @Size(max = 255) String deathCause,
            @Size(max = 255) String cullCause,
            @Size(max = 2000) String symptomNote,
            @Size(max = 2000) String treatmentNote,
            @Size(max = 2000) String remark) {
        public Request {
            deadCount = deadCount == null ? 0 : deadCount;
            cullCount = cullCount == null ? 0 : cullCount;
            sickCount = sickCount == null ? 0 : sickCount;
        }
    }
}
