package com.eggprompt.backend.farm;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FarmSetupController {
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    @GetMapping("/v1/farm-setup")
    public Setup status(Authentication authentication) {
        var user = users.findByUsernameAndDeletedAtIsNull(authentication.getName())
                .filter(account -> account.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        if (!"Farm Admin".equals(user.getUserRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์อ่านขั้นตอนตั้งค่าฟาร์ม");
        }
        if (user.getFarmId() == null) return new Setup(1, null);
        boolean completed = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.farms
                    WHERE farm_id = ? AND setup_completed_at IS NOT NULL
                )
                """, Boolean.class, user.getFarmId()));
        if (completed) return new Setup(0, user.getFarmId());
        boolean hasHouses = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM houses WHERE farm_id = ? AND deleted_at IS NULL)
                """, Boolean.class, user.getFarmId()));
        if (!hasHouses) return new Setup(2, user.getFarmId());
        return new Setup(3, user.getFarmId());
    }

    public record Setup(int step, String farmId) {}
}
