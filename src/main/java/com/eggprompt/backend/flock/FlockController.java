package com.eggprompt.backend.flock;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/v1/flocks", "/api/flocks", "/flocks"})
@RequiredArgsConstructor
public class FlockController {
    private final FlockService service;
    @GetMapping
    public List<FlockService.FlockView> list(Authentication auth, @RequestParam(required=false) String farmId) {
        return service.list(auth.getName(),farmId);
    }
    @GetMapping("/breeds")
    public List<FlockService.Breed> breeds(Authentication auth) { return service.breeds(auth.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FlockService.FlockView create(Authentication auth, @Valid @RequestBody Request request,
            @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        return farmScope == null ? service.create(auth.getName(),request)
                : service.create(auth.getName(),request,farmScope);
    }
    @PutMapping("/{id}")
    public FlockService.FlockView update(Authentication auth, @PathVariable int id, @Valid @RequestBody Request request) {
        return service.update(auth.getName(),id,request);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable int id) { service.delete(auth.getName(),id); }
    public record Request(
            @Size(max=50) @Pattern(regexp="[A-Za-z0-9._-]+",message="รหัสฝูงต้องเป็นตัวอักษรอังกฤษ ตัวเลข จุด ขีดกลาง หรือขีดล่าง") String flockCode,
            @NotNull @Positive Integer houseId,
            @NotNull @Positive Integer breedId,
            @NotNull LocalDate receivedDate,
            @NotNull @PositiveOrZero Integer initialAgeWeeks,
            @NotNull @Positive Integer initialCount,
            @NotBlank @Size(max=255) String supplier,
            @Size(max=100) String healthCertificate,
            @NotNull @PositiveOrZero @Digits(integer=10,fraction=2) BigDecimal pricePerBird,
            @NotNull @Positive @Digits(integer=10,fraction=2) BigDecimal initialAverageWeightG,
            @NotNull Boolean vaccinated) {}
}
