package com.eggprompt.backend.feed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record FeedLogRequest(
        @NotNull @Positive Integer houseId,
        @NotNull @Positive BigDecimal weightKg,
        @NotBlank String lotNumber
) {}