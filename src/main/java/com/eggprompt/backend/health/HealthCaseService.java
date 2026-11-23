package com.eggprompt.backend.health;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.eggprompt.backend.health.HealthCaseController.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthCaseService {
    private static final String CASE_PREFIX = "HEALTH_CASE ";
    private static final Pattern CASE_LINE = Pattern.compile("^HEALTH_CASE [0-9a-f-]{36} \\{");
    private static final ZoneId FARM_ZONE = ZoneId.of("Asia/Bangkok");
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final JsonMapper json;
    private final HealthLogService healthLogs;

    public record Summary(UUID id, int flockId, String houseName, String cause, int affectedCount,
                          Severity severity, Status status, String createdAt, int remainingCount) {}
    public record History(UUID id, Status status, String note, String recorder, String createdAt, Outcomes outcomes) {}
    public record Detail(Summary healthCase, List<History> history, List<Status> allowedStatuses,
                         boolean canRecordRecovery) {}

    private record StoredCase(UUID id, String cause, int affectedCount, Severity severity, Status status,
                              String createdAt, List<History> history, Integer remainingCount) {
        int remaining() {
            return remainingCount != null ? remainingCount : status == Status.RECOVERED ? 0 : affectedCount;
        }

        boolean canRecordRecovery() {
            return history.stream().anyMatch(entry -> entry.status().allowsRecoveryAfterRecording());
        }
    }
    private record DailyLog(long id, int flockId, String houseName, String remark) {}
    private record Flock(int initialCount, int currentCount, String houseName) {}
    private record Access(User actor, String farmId) {}

    private static final String LOGS = """
            SELECT d.health_log_id, d.flock_id, d.remark, h.house_name
            FROM daily_health_logs d JOIN flocks f ON f.flock_id = d.flock_id
            JOIN houses h ON h.house_id = f.house_id
            WHERE d.deleted_at IS NULL AND h.farm_id = ?
            """;
    private static final RowMapper<DailyLog> DAILY_LOG = (row, index) -> new DailyLog(
            row.getLong("health_log_id"), row.getInt("flock_id"), row.getString("house_name"), row.getString("remark"));

    public List<Summary> list(String username, String scope) {
        var rows = jdbc.query(LOGS + " AND strpos(d.remark, ?) > 0", DAILY_LOG, access(username, scope).farmId(), CASE_PREFIX);
        return rows.stream()
                .flatMap(row -> Arrays.stream(row.remark().split("\n"))
                        .filter(line -> CASE_LINE.matcher(line).find())
                        .map(line -> summary(row, decode(line))))
                .sorted(Comparator.comparing((Summary item) -> Instant.parse(item.createdAt())).reversed())
                .toList();
    }

    public Detail detail(String username, String scope, UUID id) {
        DailyLog row = find(access(username, scope).farmId(), id, false);
        return detail(row, readCase(row, id));
    }

    @Transactional
    public Detail create(String username, String scope, Create request) {
        Access access = access(username, scope);
        var flocks = jdbc.query("""
                SELECT f.initial_count, f.current_count, h.house_name FROM flocks f JOIN houses h ON h.house_id = f.house_id
                WHERE f.flock_id = ? AND h.farm_id = ? AND f.deleted_at IS NULL AND h.deleted_at IS NULL
                      AND f.status <> 'RETIRED' FOR UPDATE OF f
                """, (row, index) -> new Flock(row.getInt("initial_count"), row.getInt("current_count"), row.getString("house_name")),
                request.flockId(), access.farmId());
        if (flocks.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ไม่พบฝูงที่เปิดให้บันทึกเคสในฟาร์มนี้");
        }
        Flock flock = flocks.getFirst();
        int available = healthLogs.availableCount(request.flockId(), flock.initialCount(), flock.currentCount());
        if (request.affectedCount() > available) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "จำนวนไก่ที่ได้รับผลกระทบเกินยอดคงเหลือ");
        }

        LocalDate date = LocalDate.now(FARM_ZONE);
        History first = history(Status.REPORTED, request.note(), access.actor(), null);
        StoredCase entry = new StoredCase(UUID.randomUUID(), request.cause().strip(), request.affectedCount(),
                request.severity(), first.status(), first.createdAt(), List.of(first), request.affectedCount());
        String line = encode(entry);
        var rows = jdbc.query(LOGS + " AND d.flock_id = ? AND d.record_date = ? FOR UPDATE OF d",
                DAILY_LOG, access.farmId(), request.flockId(), date);

        if (rows.isEmpty()) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO daily_health_logs (flock_id, record_date, live_hen_count, dead_count,
                                                   cull_count, remark, created_by_user_id)
                    VALUES (?, ?, ?, 0, 0, ?, ?) RETURNING health_log_id
                    """, Long.class, request.flockId(), date, available, line, access.actor().getUserId());
            return detail(new DailyLog(id, request.flockId(), flock.houseName(), line), entry);
        }

        DailyLog row = rows.getFirst();
        writeRemark(row.id(), row.remark() == null || row.remark().isEmpty() ? line : row.remark() + "\n" + line);
        return detail(row, entry);
    }

    @Transactional
    public Detail update(String username, String scope, UUID id, Update request) {
        Access access = access(username, scope);
        int flockId = find(access.farmId(), id, false).flockId();
        // Daily health writes lock the flock before its daily rows.
        var flock = jdbc.queryForList("""
                SELECT f.initial_count, f.current_count FROM flocks f JOIN houses h ON h.house_id = f.house_id
                WHERE f.flock_id = ? AND h.farm_id = ? FOR UPDATE OF f
                """, flockId, access.farmId());
        if (flock.isEmpty()) throw notFound();
        DailyLog row = find(access.farmId(), id, true);
        StoredCase entry = readCase(row, id);
        if (entry.status() != request.expectedStatus() || !entry.history().getLast().id().equals(request.expectedHistoryId())) {
            throw new ApiException(HttpStatus.CONFLICT, "สถานะเคสเปลี่ยนแล้ว กรุณาโหลดรายละเอียดใหม่");
        }
        if (!entry.status().allowedStatuses().contains(request.status())) {
            String message = request.status() == Status.CLOSED
                    ? "ปิดเคสได้เฉพาะเมื่อพร้อมกลับโรงเรือน"
                    : "ไม่สามารถเปลี่ยนไปยังสถานะที่เลือกจากสถานะปัจจุบันได้";
            throw new ApiException(HttpStatus.CONFLICT, message);
        }
        Outcomes outcomes = request.outcomes() == null ? new Outcomes(0, 0, 0) : request.outcomes();
        if (outcomes.recovered() > 0 && !entry.canRecordRecovery()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "กรุณาบันทึกการประเมินอาการหรือการรักษาก่อนบันทึกจำนวนไก่ฟื้นตัว");
        }
        long resolved = (long) outcomes.recovered() + outcomes.dead() + outcomes.culled();
        if (resolved > entry.remaining()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "จำนวนผลการติดตามเกินจำนวนไก่ที่ยังรักษา");
        }
        int remaining = entry.remaining() - (int) resolved;
        if ((request.status() == Status.RECOVERED || request.status() == Status.CLOSED) && remaining > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ระบุผลของไก่ที่ยังรักษาให้ครบก่อนเลือกพร้อมกลับโรงเรือน");
        }

        String note = request.note() == null ? "" : request.note().strip();
        if (note.isBlank()) {
            if (entry.status() == Status.REPORTED && request.status() == Status.ISOLATED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณาระบุเหตุผลที่ต้องแยกกักกันทันที");
            }
            if (entry.status() == request.status() && resolved == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณากรอกรายละเอียดเมื่อไม่มีการเปลี่ยนสถานะหรือผลของไก่");
            }
        }
        var entries = new ArrayList<>(entry.history());
        entries.add(history(request.status(), note.isBlank() ? null : note, access.actor(), resolved > 0 ? outcomes : null));
        StoredCase updated = new StoredCase(id, entry.cause(), entry.affectedCount(), entry.severity(),
                request.status(), entry.createdAt(), entries, remaining);
        String[] lines = row.remark().split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].startsWith(prefix(id))) {
                lines[index] = encode(updated);
                break;
            }
        }
        writeRemark(row.id(), String.join("\n", lines));
        int loss = outcomes.dead() + outcomes.culled();
        if (loss > 0) {
            var counts = flock.getFirst();
            int available = healthLogs.availableCount(flockId, ((Number) counts.get("initial_count")).intValue(),
                    ((Number) counts.get("current_count")).intValue());
            if (loss > available) {
                throw new ApiException(HttpStatus.CONFLICT, "จำนวนตายและคัดทิ้งเกินยอดไก่คงเหลือของฝูง");
            }
            String cause = "จากเคสสุขภาพ: " + entry.cause();
            healthLogs.create(username, new HealthLogController.Request(flockId, LocalDate.now(FARM_ZONE),
                    available - loss, outcomes.dead(), outcomes.culled(), 0,
                    outcomes.dead() > 0 ? cause.substring(0, Math.min(255, cause.length())) : null,
                    outcomes.culled() > 0 ? cause.substring(0, Math.min(255, cause.length())) : null,
                    null, null, "เคส " + id), scope);
        }
        return detail(row, updated);
    }

    private Access access(String username, String scope) {
        User actor = users.findByUsernameAndDeletedAtIsNull(username).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        String role = actor.getUserRole();
        if (!List.of("Super Admin", "Farm Admin", "Farm Staff").contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เข้าถึงเคสสุขภาพ");
        }
        String farmId = "Super Admin".equals(role) ? scope : actor.getFarmId();
        if (farmId == null || farmId.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "กรุณาเลือกฟาร์มก่อนเข้าถึงเคสสุขภาพ");
        }
        return new Access(actor, farmId);
    }

    private DailyLog find(String farmId, UUID id, boolean lock) {
        return jdbc.query(LOGS + " AND strpos(d.remark, ?) > 0" + (lock ? " FOR UPDATE OF d" : ""),
                DAILY_LOG, farmId, prefix(id)).stream()
                .filter(row -> row.remark().lines().anyMatch(line -> line.startsWith(prefix(id))))
                .findFirst().orElseThrow(HealthCaseService::notFound);
    }

    private StoredCase readCase(DailyLog row, UUID id) {
        return row.remark().lines().filter(line -> line.startsWith(prefix(id)))
                .findFirst().map(this::decode).orElseThrow(HealthCaseService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ไม่พบเคสสุขภาพในฟาร์มนี้");
    }

    private void writeRemark(long id, String remark) {
        jdbc.update("UPDATE daily_health_logs SET remark = ?, updated_at = CURRENT_TIMESTAMP WHERE health_log_id = ?",
                remark, id);
    }

    private static String prefix(UUID id) {
        return CASE_PREFIX + id + " ";
    }

    private String encode(StoredCase entry) {
        return prefix(entry.id()) + json.writeValueAsString(entry);
    }

    private StoredCase decode(String line) {
        return json.readValue(line.substring(line.indexOf(' ', CASE_PREFIX.length()) + 1), StoredCase.class);
    }

    private static History history(Status status, String note, User actor, Outcomes outcomes) {
        return new History(UUID.randomUUID(), status, note == null ? null : note.strip(),
                actor.getUsername(), Instant.now().toString(), outcomes);
    }

    private static Detail detail(DailyLog row, StoredCase entry) {
        return new Detail(summary(row, entry), entry.history(), entry.status().allowedStatuses(), entry.canRecordRecovery());
    }

    private static Summary summary(DailyLog row, StoredCase entry) {
        return new Summary(entry.id(), row.flockId(), row.houseName(), entry.cause(), entry.affectedCount(),
                entry.severity(), entry.status(), entry.createdAt(), entry.remaining());
    }
}
