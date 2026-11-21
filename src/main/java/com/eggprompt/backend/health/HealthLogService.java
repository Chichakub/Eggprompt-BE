package com.eggprompt.backend.health;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HealthLogService {
    private static final ZoneId FARM_ZONE = ZoneId.of("Asia/Bangkok");
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final JsonMapper json;

    public record Result(long healthLogId, int flockId, LocalDate recordDate, int liveHenCount,
                         int deadCount, int cullCount, int sickCount, int currentCount) {}
    public record LossHistory(String id, LocalDate recordDate, String recordedAt, String houseName, String flockCode,
                              int deadCount, int cullCount, String deathCause, String cullCause, String remark) {}

    private record LossDay(long id, LocalDate date, String houseName, String flockCode,
                           int deadCount, int cullCount, String remark) {}

    private record Flock(String farmId, LocalDate receivedDate, int initialCount, int currentCount) {}
    private record DailyLog(long id, int deadCount, int cullCount, String symptomNote, String remark) {}
    private record Symptoms(String recordedAt, int sickCount, String symptomNote) {}
    private record EventDetails(String recordedAt, int recordedByUserId, int liveHenCount,
                                int deadCount, int cullCount, String deathCause, String cullCause,
                                String treatmentNote, String remark) {}

    private User actor(String username) {
        User actor = users.findByUsernameAndDeletedAtIsNull(username).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        String role = actor.getUserRole();
        if (!List.of("Super Admin", "Farm Admin", "Farm Staff").contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เข้าถึงข้อมูลสุขภาพไก่");
        }
        return actor;
    }

    @Transactional(readOnly = true)
    public List<LossHistory> lossHistory(String username, String farmScope) {
        User actor = actor(username);
        String farmId = "Super Admin".equals(actor.getUserRole()) ? farmScope : actor.getFarmId();
        if (!StringUtils.hasText(farmId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "กรุณาเลือกฟาร์มก่อนดูประวัติสุขภาพ");
        }
        var days = jdbc.query("""
                SELECT d.health_log_id, d.record_date, h.house_name, f.flock_code, d.dead_count, d.cull_count, d.remark
                FROM daily_health_logs d JOIN flocks f ON f.flock_id = d.flock_id
                JOIN houses h ON h.house_id = f.house_id
                WHERE h.farm_id = ? AND d.deleted_at IS NULL AND (d.dead_count > 0 OR d.cull_count > 0)
                ORDER BY d.record_date DESC, d.health_log_id DESC
                LIMIT 100
                """, (row, index) -> new LossDay(row.getLong("health_log_id"),
                row.getObject("record_date", LocalDate.class), row.getString("house_name"), row.getString("flock_code"),
                row.getInt("dead_count"), row.getInt("cull_count"), row.getString("remark")), farmId);
        return days.stream().flatMap(day -> lossEntries(day).stream()).toList();
    }

    private List<LossHistory> lossEntries(LossDay day) {
        var history = new ArrayList<LossHistory>();
        int remainingDead = day.deadCount();
        int remainingCulled = day.cullCount();
        String[] lines = day.remark() == null ? new String[0] : day.remark().split("\n");
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = lines[index].strip();
            if (!line.startsWith("{")) continue;
            try {
                var event = json.readTree(line);
                int dead = event.path("deadCount").asInt(0);
                int culled = event.path("cullCount").asInt(0);
                if (dead < 0 || culled < 0 || dead > remainingDead || culled > remainingCulled
                        || (dead == 0 && culled == 0)) continue;
                history.add(new LossHistory(day.id() + "-" + index, day.date(), event.path("recordedAt").asText(null),
                        day.houseName(), day.flockCode(), dead, culled, event.path("deathCause").asText(null),
                        event.path("cullCause").asText(null), event.path("remark").asText(null)));
                remainingDead -= dead;
                remainingCulled -= culled;
            } catch (JacksonException ignored) {
                // Plain-text or malformed legacy notes still contribute to the daily totals below.
            }
        }
        if (remainingDead > 0 || remainingCulled > 0) {
            history.add(new LossHistory(day.id() + "-daily", day.date(), null, day.houseName(), day.flockCode(),
                    remainingDead, remainingCulled, null, null, null));
        }
        return history;
    }

    @Transactional
    public Result create(String username, HealthLogController.Request request, String farmScope) {
        User actor = actor(username);
        boolean superAdmin = "Super Admin".equals(actor.getUserRole());
        String allowedFarm = superAdmin ? farmScope : actor.getFarmId();
        if (!superAdmin && allowedFarm == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "บัญชีนี้ไม่มีฟาร์มที่รับผิดชอบ");
        }

        var rows = jdbc.query("""
                SELECT h.farm_id, f.received_date, f.initial_count, f.current_count
                FROM flocks f JOIN houses h ON h.house_id = f.house_id
                WHERE f.flock_id = ? AND f.deleted_at IS NULL AND h.deleted_at IS NULL
                FOR UPDATE OF f
                """, (row, index) -> new Flock(row.getString("farm_id"),
                row.getObject("received_date", LocalDate.class), row.getInt("initial_count"),
                row.getInt("current_count")), request.flockId());
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ไม่พบฝูงไก่");
        }
        Flock flock = rows.getFirst();
        if (allowedFarm != null && !Objects.equals(allowedFarm, flock.farmId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์บันทึกข้อมูลของฝูงไก่นี้");
        }

        validate(request, flock);
        // The flock lock serializes first inserts and subsequent updates for this flock.
        var dailyLogs = jdbc.query("""
                SELECT health_log_id, dead_count, cull_count, symptom_note, remark
                FROM daily_health_logs
                WHERE flock_id = ? AND record_date = ? AND deleted_at IS NULL
                FOR UPDATE
                """, (row, index) -> new DailyLog(row.getLong("health_log_id"), row.getInt("dead_count"),
                row.getInt("cull_count"), row.getString("symptom_note"), row.getString("remark")),
                request.flockId(), request.recordDate());
        DailyLog dailyLog = dailyLogs.isEmpty() ? null : dailyLogs.getFirst();
        long deadCount = (long) request.deadCount() + (dailyLog == null ? 0 : dailyLog.deadCount());
        long cullCount = (long) request.cullCount() + (dailyLog == null ? 0 : dailyLog.cullCount());
        if (request.liveHenCount() + deadCount + cullCount > flock.initialCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "จำนวนไก่มีชีวิตรวมกับยอดตายและคัดทิ้งของวันเกินจำนวนรับเข้าฝูง");
        }
        int available = availableCount(request.flockId(), flock.initialCount(), flock.currentCount());
        long eventLosses = (long) request.deadCount() + request.cullCount();
        if (eventLosses > available) {
            throw new ApiException(HttpStatus.CONFLICT, "จำนวนตายและคัดทิ้งเกินจำนวนไก่คงเหลือของฝูง");
        }
        int currentCount = (int) (available - eventLosses);

        String recordedAt = Instant.now().toString();
        var symptoms = new Symptoms(recordedAt, request.sickCount(), clean(request.symptomNote()));
        var details = new EventDetails(recordedAt, actor.getUserId(), request.liveHenCount(),
                request.deadCount(), request.cullCount(), clean(request.deathCause()),
                clean(request.cullCause()), clean(request.treatmentNote()), clean(request.remark()));
        String symptomNote = symptoms.sickCount() > 0 || symptoms.symptomNote() != null
                ? json.writeValueAsString(symptoms) : null;
        String remark = json.writeValueAsString(details);
        long logId;
        if (dailyLog != null) {
            jdbc.update("""
                    UPDATE daily_health_logs
                    SET live_hen_count = ?, dead_count = ?, cull_count = ?,
                        symptom_note = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE health_log_id = ?
                    """, request.liveHenCount(), (int) deadCount, (int) cullCount,
                    appendNote(dailyLog.symptomNote(), symptomNote), appendNote(dailyLog.remark(), remark),
                    dailyLog.id());
            logId = dailyLog.id();
        } else {
            logId = jdbc.queryForObject("""
                    INSERT INTO daily_health_logs
                        (record_date, flock_id, live_hen_count, dead_count, cull_count,
                         symptom_note, remark, created_by_user_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING health_log_id
                    """, Long.class, request.recordDate(), request.flockId(), request.liveHenCount(),
                    request.deadCount(), request.cullCount(), symptomNote, remark, actor.getUserId());
        }
        if (currentCount != flock.currentCount()) {
            jdbc.update("""
                    UPDATE flocks SET current_count = ?, updated_at = CURRENT_TIMESTAMP WHERE flock_id = ?
                    """, currentCount, request.flockId());
        }
        return new Result(logId, request.flockId(), request.recordDate(), request.liveHenCount(),
                (int) deadCount, (int) cullCount, request.sickCount(), currentCount);
    }

    int availableCount(int flockId, int initialCount, int currentCount) {
        long recordedLosses = jdbc.queryForObject("""
                SELECT COALESCE(SUM(dead_count::bigint) + SUM(cull_count::bigint), 0)
                FROM daily_health_logs
                WHERE flock_id = ? AND deleted_at IS NULL
                """, Long.class, flockId);
        return (int) Math.max(0, Math.min(currentCount, initialCount - recordedLosses));
    }

    private String appendNote(String existing, String event) {
        if (event == null) return existing;
        return StringUtils.hasText(existing) ? existing + "\n" + event : event;
    }

    private void validate(HealthLogController.Request request, Flock flock) {
        if (request.recordDate().isBefore(flock.receivedDate()) || request.recordDate().isAfter(LocalDate.now(FARM_ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "วันที่บันทึกต้องอยู่ระหว่างวันที่รับฝูงและวันนี้");
        }
        if (request.sickCount() > request.liveHenCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "จำนวนไก่ไม่สอดคล้องกับยอดของฝูง");
        }
        if ((request.deadCount() > 0 && !StringUtils.hasText(request.deathCause()))
                || (request.cullCount() > 0 && !StringUtils.hasText(request.cullCause()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณาระบุสาเหตุการตายหรือการคัดทิ้งตามจำนวนที่บันทึก");
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
