package com.eggprompt.backend.flock;

import com.eggprompt.backend.user.*;
import com.eggprompt.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import java.sql.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.eggprompt.backend.flock.FlockController.Request;

@Service
@RequiredArgsConstructor
@Transactional
public class FlockService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public record Breed(int breedId,String breedName) {}
    public record FlockView(int flockId,String flockCode,int houseId,String houseName,String farmId,
            int breedId,String breedName,LocalDate receivedDate,int initialAgeWeeks,long ageWeeks,
            int initialCount,int currentCount,double mortalityPercent,double survivalPercent,String supplier,String healthCertificate,
            BigDecimal pricePerBird,BigDecimal initialAverageWeightG,Boolean vaccinated,String status,
            Double henDayPercent,Double eggWeightG,Double feedGramsPerBird) {}
    private User actor(String username,boolean write) {
        User u=users.findByUsernameAndDeletedAtIsNull(username).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,"ไม่พบบัญชีผู้ใช้"));
        if (!List.of("Super Admin","Farm Admin","Farm Staff").contains(u.getUserRole()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์จัดการข้อมูลฝูงไก่");
        return u;
    }
    private boolean superAdmin(User u) { return "Super Admin".equals(u.getUserRole()); }
    private static final String SELECT = """
            SELECT f.*,h.house_name,h.farm_id,b.breed_name,
            (SELECT COALESCE(sum(dead_count),0) FROM daily_health_logs d WHERE d.flock_id=f.flock_id AND d.deleted_at IS NULL) AS total_dead,
            (SELECT COALESCE(sum(cull_count),0) FROM daily_health_logs d WHERE d.flock_id=f.flock_id AND d.deleted_at IS NULL) AS total_culled,
            (SELECT SUM(e.total_egg_count)*100.0/NULLIF(MAX(d.live_hen_count),0) FROM daily_egg_logs e
              JOIN daily_health_logs d ON d.flock_id=e.flock_id AND d.record_date=e.record_date AND d.deleted_at IS NULL
              WHERE e.flock_id=f.flock_id AND e.deleted_at IS NULL GROUP BY e.record_date ORDER BY e.record_date DESC LIMIT 1) AS hen_day,
                        (SELECT SUM(feed.feed_weight_kg) * 1000.0 / NULLIF(d.live_hen_count, 0)
                         FROM daily_feed_logs feed
                         JOIN daily_health_logs d
                           ON d.flock_id = feed.flock_id
                          AND d.record_date = feed.record_date
                          AND d.deleted_at IS NULL
                         WHERE feed.flock_id = f.flock_id
                           AND feed.deleted_at IS NULL
                         GROUP BY feed.record_date, d.live_hen_count
                         ORDER BY feed.record_date DESC
                         LIMIT 1) AS feed_per_bird
            FROM flocks f
            JOIN houses h ON h.house_id=f.house_id JOIN breeds b ON b.breed_id=f.breed_id
            WHERE f.deleted_at IS NULL AND h.deleted_at IS NULL
            """;
    private FlockView view(ResultSet r,int index) throws SQLException {
        LocalDate received=r.getDate("received_date").toLocalDate();
        int initial=r.getInt("initial_count");
        long dead=r.getLong("total_dead"), culled=r.getLong("total_culled");
        int current=(int) Math.max(0, Math.min(r.getInt("current_count"), initial - dead - culled));
        return new FlockView(r.getInt("flock_id"),r.getString("flock_code"),r.getInt("house_id"),r.getString("house_name"),r.getString("farm_id"),
                r.getInt("breed_id"),r.getString("breed_name"),received,r.getInt("initial_age_weeks"),
                r.getInt("initial_age_weeks")+Math.max(0,ChronoUnit.DAYS.between(received,LocalDate.now(ZoneId.of("Asia/Bangkok"))))/7,
                initial,current,percentageOfIntake(dead,initial),percentageOfIntake(current,initial),
                r.getString("supplier"),r.getString("health_certificate"),r.getBigDecimal("price_per_bird"),r.getBigDecimal("initial_average_weight_g"),
                (Boolean)r.getObject("vaccinated"),r.getString("status"), r.getObject("hen_day")==null?null:r.getDouble("hen_day"),null,
                r.getObject("feed_per_bird")==null?null:r.getDouble("feed_per_bird"));
    }
    private static double percentageOfIntake(long count, int initialCount) {
        return initialCount > 0 ? count * 100.0 / initialCount : 0.0;
    }
    @Transactional(readOnly=true)
    public List<FlockView> list(String username,String selectedFarm) {
        User u=actor(username,false);String farm=superAdmin(u)?selectedFarm:u.getFarmId();
        if(!superAdmin(u) && farm==null)return List.of();
        return farm==null || farm.isBlank() ? jdbc.query(SELECT+" ORDER BY f.flock_id DESC",this::view)
                : jdbc.query(SELECT+" AND h.farm_id=? ORDER BY f.flock_id DESC",this::view,farm);
    }
    @Transactional(readOnly=true)
    public List<Breed> breeds(String username) {
        actor(username,false);
        return jdbc.query("SELECT breed_id,breed_name FROM breeds WHERE is_active AND deleted_at IS NULL AND (breed_code='HYL-BRN' OR lower(trim(breed_name)) IN ('hy-line brown','hy-line brown (ไฮไลน์บราวน์)')) ORDER BY CASE WHEN breed_code='HYL-BRN' THEN 0 ELSE 1 END,breed_id LIMIT 1",
                (r,i)->new Breed(r.getInt(1),r.getString(2)));
    }
    private FlockView find(User actor,int id) {
        var rows=jdbc.query(SELECT+" AND f.flock_id=?",this::view,id);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบฝูงไก่");
        FlockView flock=rows.getFirst();
        if(!superAdmin(actor) && !Objects.equals(actor.getFarmId(),flock.farmId()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์เข้าถึงฝูงไก่ของฟาร์มอื่น");
        return flock;
    }
    private void validate(User actor,Request req,Integer currentId) {
        var houses=jdbc.queryForList("SELECT farm_id,capacity,status FROM houses WHERE house_id=? AND deleted_at IS NULL FOR UPDATE",req.houseId());
        if(houses.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบโรงเรือน");
        var house=houses.getFirst();
        if(!superAdmin(actor) && !Objects.equals(actor.getFarmId(),house.get("farm_id")))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์ใช้โรงเรือนของฟาร์มอื่น");
        if(!"ACTIVE".equals(house.get("status")))throw new ApiException(HttpStatus.BAD_REQUEST,"โรงเรือนนี้ไม่ได้เปิดใช้งาน");
        if(req.initialCount()>((Number)house.get("capacity")).intValue())
            throw new ApiException(HttpStatus.BAD_REQUEST,"จำนวนไก่เกินความจุโรงเรือน");
        Integer occupied=jdbc.queryForObject("SELECT count(*) FROM flocks WHERE house_id=? AND deleted_at IS NULL AND status <> 'RETIRED' AND flock_id <> ?",Integer.class,req.houseId(),currentId==null?-1:currentId);
        if(occupied!=null && occupied>0)throw new ApiException(HttpStatus.CONFLICT,"โรงเรือนนี้มีฝูงไก่ที่ยังเลี้ยงอยู่แล้ว");
        Integer breed=jdbc.queryForObject("SELECT count(*) FROM breeds WHERE breed_id=? AND is_active AND deleted_at IS NULL AND (breed_code='HYL-BRN' OR lower(trim(breed_name)) IN ('hy-line brown','hy-line brown (ไฮไลน์บราวน์)'))",Integer.class,req.breedId());
        if(breed==null || breed==0)throw new ApiException(HttpStatus.BAD_REQUEST,"ไม่พบสายพันธุ์ Hy-Line Brown ที่เปิดใช้งาน");
        if(req.receivedDate().isAfter(LocalDate.now(ZoneId.of("Asia/Bangkok"))))
            throw new ApiException(HttpStatus.BAD_REQUEST,"วันที่รับเข้าต้องไม่เป็นอนาคต");
        if(req.flockCode()!=null && !req.flockCode().isBlank()) {
            Integer duplicate=jdbc.queryForObject("SELECT count(*) FROM flocks WHERE flock_code=? AND deleted_at IS NULL AND flock_id <> ?",Integer.class,req.flockCode(),currentId==null?-1:currentId);
            if(duplicate!=null && duplicate>0)throw new ApiException(HttpStatus.CONFLICT,"รหัสฝูงนี้ถูกใช้งานแล้ว");
        }
    }
    public FlockView create(String username,Request req,String farmScope) {
        actor(username,true);
        var farms=jdbc.queryForList("SELECT farm_id FROM houses WHERE house_id=? AND deleted_at IS NULL",req.houseId());
        if(farms.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบโรงเรือน");
        if(farmScope!=null && !Objects.equals(farmScope,farms.getFirst().get("farm_id")))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์เพิ่มฝูงไก่ในโรงเรือนนี้");
        return create(username,req);
    }
    public FlockView create(String username,Request req) {
        User actor=actor(username,true);validate(actor,req,null);
        String code=req.flockCode()==null?"FLK-"+UUID.randomUUID():req.flockCode();
        try {
            Integer id=jdbc.queryForObject("""
                    INSERT INTO flocks(flock_code,house_id,breed_id,received_date,initial_age_weeks,initial_count,current_count,
                    supplier,health_certificate,price_per_bird,initial_average_weight_g,vaccinated)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING flock_id
                    """,Integer.class,code,req.houseId(),req.breedId(),req.receivedDate(),req.initialAgeWeeks(),req.initialCount(),req.initialCount(),
                    req.supplier().trim(),req.healthCertificate(),req.pricePerBird(),req.initialAverageWeightG(),req.vaccinated());
            FlockView result=find(actor,id);
            jdbc.update("UPDATE farms SET setup_completed_at=COALESCE(setup_completed_at,CURRENT_TIMESTAMP) WHERE farm_id=?",result.farmId());
            if(Objects.equals(actor.getFarmId(),result.farmId()) && actor.isOnboardingRequired()) {
                actor.setOnboardingRequired(false);users.save(actor);
            }
            return result;
        } catch(DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT,"รหัสฝูงหรือโรงเรือนนี้ถูกใช้งานแล้ว กรุณาตรวจสอบข้อมูล"); }
    }
    public FlockView update(String username,int id,Request req) {
        User actor=actor(username,true);
        if("Farm Staff".equals(actor.getUserRole()))throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์แก้ไขข้อมูลฝูงไก่");
        find(actor,id);
        validate(actor,req,id);
        jdbc.queryForList("SELECT flock_id FROM flocks WHERE flock_id=? AND deleted_at IS NULL FOR UPDATE",id);
        FlockView previous=find(actor,id);
        var removed=jdbc.queryForObject("SELECT COALESCE(sum(dead_count::bigint)+sum(cull_count::bigint),0) FROM daily_health_logs WHERE flock_id=? AND deleted_at IS NULL",Long.class,id);
        long losses=Math.max(Math.max(0,previous.initialCount()-previous.currentCount()), removed==null?0:removed);
        if(req.initialCount()<losses)throw new ApiException(HttpStatus.BAD_REQUEST,"จำนวนรับเข้าต้องไม่น้อยกว่าจำนวนตายและคัดทิ้งสะสม");
        if(!previous.receivedDate().equals(req.receivedDate()) || previous.houseId()!=req.houseId()) {
            Integer logs=jdbc.queryForObject("SELECT (SELECT count(*) FROM daily_health_logs WHERE flock_id=? AND deleted_at IS NULL)+(SELECT count(*) FROM daily_egg_logs WHERE flock_id=? AND deleted_at IS NULL)+(SELECT count(*) FROM daily_feed_logs WHERE flock_id=? AND deleted_at IS NULL)",Integer.class,id,id,id);
            if(logs!=null && logs>0)throw new ApiException(HttpStatus.CONFLICT,"ฝูงที่มีบันทึกประจำวันแล้วไม่สามารถเปลี่ยนโรงเรือนหรือวันที่รับเข้าได้");
        }
        try {
            jdbc.update("""
                    UPDATE flocks SET flock_code=?,house_id=?,breed_id=?,received_date=?,initial_age_weeks=?,initial_count=?,current_count=?,
                    supplier=?,health_certificate=?,price_per_bird=?,initial_average_weight_g=?,vaccinated=?,updated_at=CURRENT_TIMESTAMP
                    WHERE flock_id=? AND deleted_at IS NULL
                    """,req.flockCode()==null?previous.flockCode():req.flockCode(),req.houseId(),req.breedId(),req.receivedDate(),req.initialAgeWeeks(),req.initialCount(),req.initialCount()-losses,
                    req.supplier().trim(),req.healthCertificate(),req.pricePerBird(),req.initialAverageWeightG(),req.vaccinated(),id);
            return find(actor,id);
        } catch(DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT,"รหัสฝูงหรือโรงเรือนซ้ำ"); }
    }
    public void delete(String username,int id) {
        User actor=actor(username,true);
        if (!superAdmin(actor) && !"Farm Admin".equals(actor.getUserRole()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์ลบข้อมูลฝูงไก่");
        find(actor,id);
        jdbc.update("UPDATE flocks SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE flock_id=?",id);
    }
}
