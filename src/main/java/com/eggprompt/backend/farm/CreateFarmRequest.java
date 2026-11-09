package com.eggprompt.backend.farm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateFarmRequest(
        @NotBlank @Size(max = 255) String farmName,
        @NotBlank @Size(max = 1000) String address,
        @NotBlank @Size(max = 100) String farmRegistrationNumber,
        @NotBlank @Size(max = 20) String taxId,
        @NotBlank @Size(max = 20) String contactPhone,
        @NotNull LocalDate operationStartDate,
        @NotBlank @Size(max = 255) String ownerName,
        List<@NotNull @Valid AccountRequest> accounts
) {
    public CreateFarmRequest {
        if (accounts == null) {
            accounts = List.of();
        }
    }

    public record AccountRequest(
            @NotBlank String role,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,24}") String username,
            @NotBlank String temporaryPassword
    ) {}
}
