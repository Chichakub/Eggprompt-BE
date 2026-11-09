package com.eggprompt.backend.farm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmCreatedResponse create(
            Authentication authentication,
            @Valid @RequestBody CreateFarmRequest request
    ) {
        String farmId = farmService.create(authentication.getName(), request);
        return new FarmCreatedResponse(farmId);
    }

    public record FarmCreatedResponse(String farmId) {}
}