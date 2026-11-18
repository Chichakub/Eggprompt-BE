package com.eggprompt.backend.flock;

import com.eggprompt.backend.auth.JwtAuthenticationFilter;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.house.HouseRepository;
import com.eggprompt.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/flocks")
@RequiredArgsConstructor
public class FlockController {
    private final JdbcTemplate jdbc;
    private final HouseRepository houses;
    private final UserRepository users;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Created create(Authentication auth, @Valid @RequestBody Request request,
                          @RequestAttribute(name = JwtAuthenticationFilter.FARM_SCOPE, required = false) String farmScope) {
        var actor = users.findByUsernameAndDeletedAtIsNull(auth.getName())
                .filter(user -> user.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        var house = houses.findByHouseIdAndDeletedAtIsNull(request.houseId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ไม่พบโรงเรือน"));

        if ((farmScope != null && !farmScope.equals(house.getFarmId()))
                || (!"Super Admin".equals(actor.getUserRole())
                && (!"Farm Admin".equals(actor.getUserRole()) || !house.getFarmId().equals(actor.getFarmId())))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เพิ่มฝูงไก่ในโรงเรือนนี้");
        }

        String code = "FLK-" + UUID.randomUUID();
        var ids = jdbc.query("""
                INSERT INTO public.flocks (
                    flock_code, house_id, breed_id, received_date, initial_age_weeks,
                    initial_count, current_count, supplier, health_certificate,
                    price_per_bird, initial_average_weight_g, vaccinated
                )
                SELECT ?, ?, breed_id, ?, ?, ?, ?, ?, ?, ?, ?, ?
                FROM public.breeds
                WHERE breed_id = ? AND breed_code = 'HYL-BRN' AND is_active AND deleted_at IS NULL
                RETURNING flock_id
                """, (row, number) -> row.getInt("flock_id"),
                code, request.houseId(), request.receivedDate(), request.initialAgeWeeks(),
                request.initialCount(), request.initialCount(), request.supplier().trim(),
                request.healthCertificate() == null || request.healthCertificate().isBlank()
                        ? null : request.healthCertificate().trim(),
                request.pricePerBird(), request.initialAverageWeightG(), request.vaccinated(), request.breedId());

        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณาเลือกสายพันธุ์ Hy-Line Brown ที่เปิดใช้งาน");
        }

        jdbc.update("""
                UPDATE public.farms
                SET setup_completed_at = COALESCE(setup_completed_at, CURRENT_TIMESTAMP)
                WHERE farm_id = ?
                """, house.getFarmId());

        return new Created(ids.get(0), code);
    }

    public record Request(
            @NotNull @Positive Integer houseId,
            @NotNull @Positive Integer breedId,
            @NotNull LocalDate receivedDate,
            @NotNull @PositiveOrZero Integer initialAgeWeeks,
            @NotNull @Positive Integer initialCount,
            @NotBlank @Size(max = 255) String supplier,
            @Size(max = 100) String healthCertificate,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal pricePerBird,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal initialAverageWeightG,
            @NotNull Boolean vaccinated
    ) {}

    public record Created(int flockId, String flockCode) {}
}
