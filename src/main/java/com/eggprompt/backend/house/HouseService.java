package com.eggprompt.backend.house;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HouseService {

    private static final String SUPER_ADMIN = "Super Admin";
    private static final String FARM_ADMIN = "Farm Admin";

    private final HouseRepository houses;
    private final UserRepository users;

     /* Super Admin เห็นทุกฟาร์ม (กรองด้วย farmId ได้) */
    @Transactional(readOnly = true)
    public List<HouseResponse> list(String username, String farmIdFilter) {
        User actor = activeUser(username);

        List<House> result;
        if (isSuperAdmin(actor)) {
            result = (farmIdFilter == null || farmIdFilter.isBlank())
                    ? houses.findByDeletedAtIsNullOrderByFarmIdAscHouseIdAsc()
                    : houses.findByFarmIdAndDeletedAtIsNullOrderByHouseIdAsc(farmIdFilter.trim());
        } else if (actor.getFarmId() == null) {
            result = List.of();
        } else {
            // ไม่สนใจ farmId ที่ส่งมา เพื่อกันดูข้อมูลฟาร์มอื่น
            result = houses.findByFarmIdAndDeletedAtIsNullOrderByHouseIdAsc(actor.getFarmId());
        }
        return result.stream().map(HouseResponse::from).toList();
    }

    @Transactional
    public HouseResponse create(String username, HouseRequest req) {
        User actor = writer(username);

        String farmId;
        if (isSuperAdmin(actor)) {
            if (req.farmId() == null || req.farmId().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Super Admin ต้องระบุ farmId");
            }
            farmId = req.farmId().trim();
        } else {
            farmId = actor.getFarmId();
            if (farmId == null) {
                throw new ApiException(HttpStatus.CONFLICT, "กรุณาตั้งค่าฟาร์มให้เรียบร้อยก่อนเพิ่มโรงเรือน");
            }
        }

        String name = req.houseName().trim();
        if (houses.existsByFarmIdAndHouseNameAndDeletedAtIsNull(farmId, name)) {
            throw duplicateName();
        }

        House house = new House();
        house.setFarmId(farmId);
        apply(house, req);
        if (house.getStatus() == null) house.setStatus("ACTIVE");
        return HouseResponse.from(save(house));
    }

    @Transactional
    public HouseResponse update(String username, Integer id, HouseRequest req) {
        User actor = writer(username);
        House house = find(actor, id);

        String name = req.houseName().trim();
        if (houses.existsByFarmIdAndHouseNameAndDeletedAtIsNullAndHouseIdNot(
                house.getFarmId(), name, id)) {
            throw duplicateName();
        }

        apply(house, req);   // ไม่แตะ farmId: ย้ายโรงเรือนข้ามฟาร์มไม่ได้
        return HouseResponse.from(save(house));
    }

    @Transactional
    public void delete(String username, Integer id) {
        User actor = writer(username);
        House house = find(actor, id);

        if (houses.countActiveFlocks(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "ไม่สามารถลบโรงเรือนที่ยังมีฝูงเลี้ยงอยู่ กรุณาปิดฝูงก่อน");
        }
        house.setDeletedAt(Instant.now());
        houses.save(house);
    }

    private User activeUser(String username) {
        User actor = users.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        if (!actor.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "บัญชีนี้ถูกระงับ");
        }
        return actor;
    }

    /* ผู้ที่เพิ่ม/แก้ไข/ลบได้: Super Admin และ Farm Admin*/
    private User writer(String username) {
        User actor = activeUser(username);
        if (!isSuperAdmin(actor) && !FARM_ADMIN.equals(actor.getUserRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์จัดการโรงเรือน");
        }
        return actor;
    }

    private boolean isSuperAdmin(User u) {
        return SUPER_ADMIN.equals(u.getUserRole());
    }

    private House find(User actor, Integer id) {
        House house = houses.findByHouseIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ไม่พบโรงเรือน"));
        if (!isSuperAdmin(actor) && !Objects.equals(actor.getFarmId(), house.getFarmId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ไม่พบโรงเรือน");
        }
        return house;
    }

    private void apply(House h, HouseRequest r) {
        h.setHouseName(r.houseName().trim());
        h.setHouseType(r.houseType().trim());
        h.setOpeningDate(r.openingDate());
        h.setWidthM(r.widthM());
        h.setLengthM(r.lengthM());
        h.setCapacity(r.capacity());
        if (r.status() != null) h.setStatus(r.status());
        h.setVentilationFanCount(r.ventilationFanCount());
        h.setWaterSystem(clean(r.waterSystem()));
        h.setFeedingSystem(clean(r.feedingSystem()));
        h.setEggCollectionSystem(clean(r.eggCollectionSystem()));
        h.setLightingProgram(clean(r.lightingProgram()));
        h.setLastBuiltOrRenovatedDate(r.lastBuiltOrRenovatedDate());
    }

    private String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private House save(House house) {
        try {
            return houses.saveAndFlush(house);
        } catch (DataIntegrityViolationException e) {
            // กรณีชนกันตอนบันทึกพร้อมกัน หรือ farmId ที่ระบุไม่มีอยู่จริง
            throw new ApiException(HttpStatus.CONFLICT, "ชื่อโรงเรือนซ้ำ หรือไม่พบฟาร์มที่ระบุ");
        }
    }

    private ApiException duplicateName() {
        return new ApiException(HttpStatus.CONFLICT, "ชื่อโรงเรือนนี้มีอยู่แล้วในฟาร์ม");
    }
}