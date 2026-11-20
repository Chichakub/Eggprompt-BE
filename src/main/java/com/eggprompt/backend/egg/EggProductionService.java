package com.eggprompt.backend.egg;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.math.*;
import java.util.*;
import static com.eggprompt.backend.egg.EggProductionController.Request;

@Service
@RequiredArgsConstructor
@Transactional
public class EggProductionService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public record Summary(long saleableEggCount, long defectiveQty, long totalEggCount,
                          BigDecimal totalTrayCount, BigDecimal defectivePercent) {}
    public record Result(long eggLogId, int flockId, LocalDate recordDate, Summary summary) {}
    public static Summary summarize(Request r) {
        long saleable=(long)r.gradeJumboQty()+r.grade0Qty()+r.grade1Qty()+r.grade2Qty()+r.grade3Qty()+r.grade4Qty()+r.grade5Qty();
        return totals(saleable,r.defectiveQty());
    }
    public static Summary totals(long saleable,long defective) {
        long total=Math.addExact(saleable,defective);
        return new Summary(saleable,defective,total,
                BigDecimal.valueOf(saleable).divide(BigDecimal.valueOf(30),2,RoundingMode.HALF_UP),
                total==0 ? new BigDecimal("0.00") : BigDecimal.valueOf(defective).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP));
    }
    public record DailySummary(LocalDate recordDate,Summary summary) {}
    public record FlockOverview(int flockId,LocalDate from,LocalDate to,int recordedDays,
                                Summary summary,List<DailySummary> daily) {}
    @Transactional(readOnly=true)
    public FlockOverview overview(String username,int flockId,LocalDate from,LocalDate to,String farmScope) {
        if(flockId<=0 || (from!=null && to!=null && from.isAfter(to)))
            throw new ApiException(HttpStatus.BAD_REQUEST,"รหัสฝูงหรือช่วงวันที่ไม่ถูกต้อง");
        var actor=users.findByUsernameAndDeletedAtIsNull(username).filter(u->u.isActive())
                .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"ไม่พบบัญชีผู้ใช้"));
        if(!List.of("Super Admin","Farm Admin","Farm Staff").contains(actor.getUserRole()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์ดูผลผลิตไข่");
        var flocks=jdbc.queryForList("""
                SELECT h.farm_id FROM public.flocks f JOIN public.houses h ON h.house_id=f.house_id
                WHERE f.flock_id=? AND f.deleted_at IS NULL AND h.deleted_at IS NULL
                """,flockId);
        if(flocks.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบฝูงไก่");
        Object farm=flocks.getFirst().get("farm_id");
        if((farmScope!=null && !Objects.equals(farmScope,farm))
                || (!"Super Admin".equals(actor.getUserRole()) && !Objects.equals(actor.getFarmId(),farm)))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์ดูข้อมูลของฟาร์มอื่น");
        String sql="""
                SELECT record_date,SUM(saleable_egg_count) AS saleable,SUM(defective_qty) AS defective
                FROM public.daily_egg_logs WHERE flock_id=? AND deleted_at IS NULL
                """;
        List<Object> args=new ArrayList<>();args.add(flockId);
        if(from!=null){sql+=" AND record_date>=?";args.add(from);}
        if(to!=null){sql+=" AND record_date<=?";args.add(to);}
        sql+=" GROUP BY record_date ORDER BY record_date";
        List<DailySummary> daily=jdbc.query(sql,(r,i)->new DailySummary(r.getDate("record_date").toLocalDate(),
                totals(r.getLong("saleable"),r.getLong("defective"))),args.toArray());
        long saleable=0,defective=0;
        for(var day:daily){saleable=Math.addExact(saleable,day.summary().saleableEggCount());defective=Math.addExact(defective,day.summary().defectiveQty());}
        return new FlockOverview(flockId,from,to,daily.size(),totals(saleable,defective),List.copyOf(daily));
    }
    private com.eggprompt.backend.user.User actor(String username,boolean manage) {
        var u=users.findByUsernameAndDeletedAtIsNull(username).filter(x->x.isActive())
                .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"ไม่พบบัญชีผู้ใช้"));
        if(!List.of("Super Admin","Farm Admin","Farm Staff").contains(u.getUserRole())
                || (manage && "Farm Staff".equals(u.getUserRole())))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์แก้ไขหรือลบบันทึกผลผลิตไข่");
        return u;
    }
    public record Log(long eggLogId,String houseName,String flockCode,String recorder,Request values,Summary summary) {}
    @Transactional(readOnly=true)
    public List<Log> logs(String username,String selectedFarm) {
        var u=actor(username,false);
        String farm="Super Admin".equals(u.getUserRole())?selectedFarm:u.getFarmId();
        if(farm==null || farm.isBlank())return List.of();
        return jdbc.query("""
            SELECT e.*,h.house_name,f.flock_code,u.username FROM public.daily_egg_logs e
            JOIN public.flocks f ON f.flock_id=e.flock_id JOIN public.houses h ON h.house_id=f.house_id
            JOIN public.users u ON u.user_id=e.created_by_user_id
            WHERE e.deleted_at IS NULL AND f.deleted_at IS NULL AND h.deleted_at IS NULL AND h.farm_id=?
            ORDER BY e.record_date DESC,e.egg_log_id DESC
            """,(r,i)->{
                var values=new Request(r.getInt("flock_id"),r.getDate("record_date").toLocalDate(),
                    r.getInt("grade_jumbo_qty"),r.getInt("grade_0_qty"),r.getInt("grade_1_qty"),r.getInt("grade_2_qty"),
                    r.getInt("grade_3_qty"),r.getInt("grade_4_qty"),r.getInt("grade_5_qty"),r.getInt("defective_qty"),
                    r.getString("egg_lot_number"),r.getString("collection_round"));
                return new Log(r.getLong("egg_log_id"),r.getString("house_name"),r.getString("flock_code"),
                    r.getString("username"),values,summarize(values));
            },farm);
    }
    private void checkLog(com.eggprompt.backend.user.User u,long id,String scope) {
        var rows=jdbc.queryForList("""
            SELECT h.farm_id FROM public.daily_egg_logs e JOIN public.flocks f ON f.flock_id=e.flock_id
            JOIN public.houses h ON h.house_id=f.house_id WHERE e.egg_log_id=? AND e.deleted_at IS NULL
            AND f.deleted_at IS NULL AND h.deleted_at IS NULL FOR UPDATE OF e
            """,id);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบบันทึกผลผลิตไข่");
        Object farm=rows.getFirst().get("farm_id");
        if((scope!=null && !Objects.equals(scope,farm)) || (!"Super Admin".equals(u.getUserRole()) && !Objects.equals(u.getFarmId(),farm)))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์จัดการข้อมูลของฟาร์มอื่น");
    }
    public void delete(String username,long id,String scope) {
        var u=actor(username,true);checkLog(u,id,scope);
        jdbc.update("UPDATE public.daily_egg_logs SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE egg_log_id=?",id);
    }
    public Result update(String username,long id,Request r,String scope) {
        actor(username,true);
        // Keep the original flock/date so historical records cannot be moved to another flock.
        var old=jdbc.queryForList("SELECT flock_id,record_date FROM public.daily_egg_logs WHERE egg_log_id=? AND deleted_at IS NULL",id);
        if(old.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบบันทึกผลผลิตไข่");
        if(!Objects.equals(old.getFirst().get("flock_id"),r.flockId()) || !Objects.equals(old.getFirst().get("record_date"),java.sql.Date.valueOf(r.recordDate())))
            throw new ApiException(HttpStatus.BAD_REQUEST,"ไม่สามารถเปลี่ยนฝูงหรือวันที่ของบันทึกเดิมได้");
        return save(username,r,scope,id);
    }
    public Result create(String username,Request r,String farmScope) {
        try { return save(username,r,farmScope,null); }
        catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT,"มีบันทึกของฝูง วัน และรอบนี้แล้ว");
        }
    }
    private Result save(String username, Request r, String farmScope,Long editingId) {

        var actor=users.findByUsernameAndDeletedAtIsNull(username).filter(u->u.isActive())
                .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"ไม่พบบัญชีผู้ใช้"));
        if(!List.of("Super Admin","Farm Admin","Farm Staff").contains(actor.getUserRole()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์บันทึกผลผลิตไข่");
        var flocks=jdbc.queryForList("""
                SELECT h.farm_id,f.received_date FROM public.flocks f
                JOIN public.houses h ON h.house_id=f.house_id
                WHERE f.flock_id=? AND f.deleted_at IS NULL AND h.deleted_at IS NULL FOR UPDATE OF f
                """,r.flockId());
        if(flocks.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ไม่พบฝูงไก่");
        var flock=flocks.getFirst();
        if((farmScope!=null && !Objects.equals(farmScope,flock.get("farm_id")))
                || (!"Super Admin".equals(actor.getUserRole()) && !Objects.equals(actor.getFarmId(),flock.get("farm_id"))))
            throw new ApiException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์บันทึกข้อมูลของฟาร์มอื่น");
        LocalDate received=((java.sql.Date)flock.get("received_date")).toLocalDate();
        if(r.recordDate().isBefore(received) || r.recordDate().isAfter(LocalDate.now(ZoneId.of("Asia/Bangkok"))))
            throw new ApiException(HttpStatus.BAD_REQUEST,"วันที่บันทึกต้องไม่ก่อนรับฝูงไก่และไม่เป็นอนาคต");
        if(editingId!=null)checkLog(actor,editingId,farmScope);
        Boolean exists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public.daily_egg_logs WHERE flock_id=? AND record_date=? AND (collection_round=? OR collection_round='DAILY' OR ?='DAILY') AND egg_log_id<>? AND deleted_at IS NULL)",Boolean.class,r.flockId(),r.recordDate(),r.collectionRound(),r.collectionRound(),editingId==null?-1L:editingId);
        if(Boolean.TRUE.equals(exists))throw new ApiException(HttpStatus.CONFLICT,"ฝูงไก่นี้มีบันทึกในวันและรอบที่เลือกแล้ว");
        if(summarize(r).totalEggCount()>Integer.MAX_VALUE)
            throw new ApiException(HttpStatus.BAD_REQUEST,"จำนวนไข่รวมมากเกินไป");
        if(editingId!=null) {
            jdbc.update("""
                UPDATE public.daily_egg_logs SET grade_jumbo_qty=?,grade_0_qty=?,grade_1_qty=?,grade_2_qty=?,
                grade_3_qty=?,grade_4_qty=?,grade_5_qty=?,defective_qty=?,egg_lot_number=?,collection_round=?,updated_at=CURRENT_TIMESTAMP
                WHERE egg_log_id=? AND deleted_at IS NULL
                """,r.gradeJumboQty(),r.grade0Qty(),r.grade1Qty(),r.grade2Qty(),r.grade3Qty(),r.grade4Qty(),r.grade5Qty(),
                    r.defectiveQty(),r.eggLotNumber(),r.collectionRound(),editingId);
            return new Result(editingId,r.flockId(),r.recordDate(),summarize(r));
        }
        Long id=jdbc.queryForObject("""
                INSERT INTO public.daily_egg_logs(flock_id,record_date,grade_jumbo_qty,grade_0_qty,
                grade_1_qty,grade_2_qty,grade_3_qty,grade_4_qty,grade_5_qty,defective_qty,egg_lot_number,created_by_user_id,collection_round)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING egg_log_id
                """,Long.class,r.flockId(),r.recordDate(),r.gradeJumboQty(),r.grade0Qty(),r.grade1Qty(),r.grade2Qty(),
                r.grade3Qty(),r.grade4Qty(),r.grade5Qty(),r.defectiveQty(),r.eggLotNumber(),actor.getUserId(),r.collectionRound());
        return new Result(id,r.flockId(),r.recordDate(),summarize(r));
    }
}
