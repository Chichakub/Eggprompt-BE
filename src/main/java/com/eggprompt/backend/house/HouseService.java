package com.eggprompt.backend.house;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HouseService {

    private final HouseRepository houses;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<HouseResponse> list(String username, String farmIdFilter) {
        User actor = users.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));
        if (!actor.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "บัญชีนี้ถูกระงับ");
        }

        List<House> result;
        if ("Super Admin".equals(actor.getUserRole())) {
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
}