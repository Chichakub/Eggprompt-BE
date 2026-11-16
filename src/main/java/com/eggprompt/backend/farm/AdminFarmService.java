package com.eggprompt.backend.farm;

import com.eggprompt.backend.house.HouseRepository;
import com.eggprompt.backend.user.*;
import com.eggprompt.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AdminFarmService {
    private final FarmRepository farms;
    private final HouseRepository houses;
    private final UserRepository users;
    public record FarmView(String farmId, String farmName, String ownerName, String adminName,
            String address, String farmRegistrationNumber, String taxId, LocalDate operationStartDate,
            String contactPhone, String email, boolean active, long houseCount, long userCount) {}
    public record FarmPage(List<FarmView> content, long totalElements, int totalPages, int page) {}
    public record Summary(long farms, long houses, long capacity, long users, long suspended,
                          Map<String,Long> usersByRole) {}
    private void authorize(String username) {
        User actor = users.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        if (!actor.isActive() || !"Super Admin".equals(actor.getUserRole()))
            throw new ApiException(HttpStatus.FORBIDDEN, "เฉพาะ Super Admin เท่านั้น");
    }
    private Specification<Farm> alive() { return (r,q,cb) -> cb.isNull(r.get("deletedAt")); }
    private FarmView view(Farm f) {
        var admins = users.findByFarmIdAndUserRoleAndDeletedAtIsNullOrderByUserIdAsc(f.getFarmId(), "Farm Admin");
        String admin = admins.isEmpty() ? null : (admins.getFirst().getFullName() == null
                ? admins.getFirst().getUsername() : admins.getFirst().getFullName());
        return new FarmView(f.getFarmId(), f.getFarmName(), f.getOwnerName(), admin, f.getAddress(),
                f.getFarmRegistrationNumber(), f.getTaxId(), f.getOperationStartDate(), f.getContactPhone(),
                f.getEmail(), f.isActive(), houses.countByFarmIdAndDeletedAtIsNull(f.getFarmId()),
                users.countByFarmIdAndDeletedAtIsNull(f.getFarmId()));
    }
    public FarmPage list(String username, String search, String status, int page, int size) {
        authorize(username);
        if (page < 0 || size < 1 || size > 100 || search.length() > 100 || !List.of("all","active","suspended").contains(status))
            throw new ApiException(HttpStatus.BAD_REQUEST, "ค่าการค้นหาหรือแบ่งหน้าไม่ถูกต้อง");
        Specification<Farm> spec = alive();
        if (!status.equals("all")) spec = spec.and((r,q,cb) -> cb.equal(r.get("active"), status.equals("active")));
        if (!search.isBlank()) {
            String value = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
            spec = spec.and((r,q,cb) -> cb.or(cb.like(cb.lower(r.get("farmName")),value,'\\'),
                    cb.like(cb.lower(r.get("ownerName")),value,'\\'), cb.like(cb.lower(r.get("farmId")),value,'\\')));
        }
        Page<Farm> result = farms.findAll(spec,PageRequest.of(page,size,Sort.by("farmId")));
        return new FarmPage(result.getContent().stream().map(this::view).toList(),result.getTotalElements(),result.getTotalPages(),page);
    }
    public Summary summary(String username) {
        authorize(username);
        Map<String,Long> roles = new LinkedHashMap<>();
        for (String role : List.of("Super Admin","Farm Admin","Farm Staff","Finance Staff"))
            roles.put(role, users.countByUserRoleAndDeletedAtIsNull(role));
        return new Summary(farms.count(alive()), houses.countByDeletedAtIsNull(), houses.totalCapacity(),
                users.countByDeletedAtIsNull(), farms.count(alive().and((r,q,cb) -> cb.isFalse(r.get("active")))),roles);
    }
    public FarmView own(String username) {
        User actor = users.findByUsernameAndDeletedAtIsNull(username)
                .filter(User::isActive).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,"ไม่พบบัญชีผู้ใช้"));
        if (actor.getFarmId() == null) throw new ApiException(HttpStatus.NOT_FOUND,"ยังไม่ได้ลงทะเบียนฟาร์ม");
        return view(farms.findById(actor.getFarmId()).filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ไม่พบฟาร์มนี้")));
    }
    public FarmView detail(String username,String id) {
        authorize(username);
        Farm f = farms.findById(id).filter(farm -> farm.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ไม่พบฟาร์มนี้"));
        return view(f);
    }
}
