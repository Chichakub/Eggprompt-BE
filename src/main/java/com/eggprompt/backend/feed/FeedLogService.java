package com.eggprompt.backend.feed;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.house.House;
import com.eggprompt.backend.house.HouseRepository;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedLogService {

    private static final ZoneId FARM_ZONE = ZoneId.of("Asia/Bangkok");
    private static final List<String> ALLOWED_ROLES =
            List.of("Super Admin", "Farm Admin", "Farm Staff");

    private final UserRepository users;
    private final HouseRepository houses;
    private final JdbcTemplate jdbc;

    public record Result(
            long feedLogId,
            int houseId,
            LocalDate recordDate,
            BigDecimal weightKg,
            String lotNumber
    ) {}

    @Transactional
    public Result create(String username, FeedLogRequest request, String farmScope) {
        User actor = requireActiveUser(username);
        House house = requireHouseAccess(actor, request.houseId(), farmScope);

        List<Integer> flockIds = jdbc.queryForList("""
                SELECT flock_id
                FROM flocks
                WHERE house_id = ?
                  AND deleted_at IS NULL
                  AND status <> 'RETIRED'
                """, Integer.class, house.getHouseId());

        if (flockIds.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "โรงเรือนนี้ไม่มีฝูงไก่ที่กำลังเลี้ยง");
        }

        if (flockIds.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "โรงเรือนนี้มีฝูงไก่ที่กำลังเลี้ยงมากกว่าหนึ่งฝูง");
        }

        int flockId = flockIds.getFirst();
        LocalDate recordDate = LocalDate.now(FARM_ZONE);
        String lotNumber = request.lotNumber().strip();

        Long feedLogId = jdbc.queryForObject("""
                INSERT INTO daily_feed_logs
                    (flock_id, record_date, feed_weight_kg, lot_number,
                     created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING feed_log_id
                """, Long.class, flockId, recordDate, request.weightKg(),
                lotNumber, actor.getUserId());

        return new Result(feedLogId, house.getHouseId(), recordDate,
                request.weightKg(), lotNumber);
    }

    @Transactional(readOnly = true)
    public List<Result> findByHouse(String username, int houseId, String farmScope) {
        User actor = requireActiveUser(username);
        requireHouseAccess(actor, houseId, farmScope);

        return jdbc.query("""
                SELECT d.feed_log_id, d.record_date, d.feed_weight_kg, d.lot_number
                FROM daily_feed_logs d
                JOIN flocks f ON f.flock_id = d.flock_id
                WHERE f.house_id = ?
                  AND d.deleted_at IS NULL
                ORDER BY d.record_date DESC, d.feed_log_id DESC
                """, (row, index) -> new Result(
                row.getLong("feed_log_id"),
                houseId,
                row.getObject("record_date", LocalDate.class),
                row.getBigDecimal("feed_weight_kg"),
                row.getString("lot_number")
        ), houseId);
    }

    private User requireActiveUser(String username) {
        return users.findByUsernameAndDeletedAtIsNull(username)
                .filter(User::isActive)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
    }

    private House requireHouseAccess(User actor, int houseId, String farmScope) {
        String role = actor.getUserRole();
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "ไม่มีสิทธิ์เข้าถึงข้อมูลการให้อาหาร");
        }

        boolean superAdmin = "Super Admin".equals(role);
        String allowedFarm = superAdmin ? farmScope : actor.getFarmId();
        if (!superAdmin && allowedFarm == null) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "บัญชีนี้ไม่มีฟาร์มที่รับผิดชอบ");
        }

        House house = houses.findByHouseIdAndDeletedAtIsNull(houseId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "ไม่พบโรงเรือน"));

        if (allowedFarm != null && !allowedFarm.equals(house.getFarmId())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "ไม่มีสิทธิ์เข้าถึงข้อมูลของโรงเรือนนี้");
        }

        return house;
    }
}