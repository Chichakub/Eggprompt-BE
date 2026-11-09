package com.eggprompt.backend.farm;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.AdminUserController.CreateUserRequest;
import com.eggprompt.backend.user.AdminUserService;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farms;
    private final UserRepository users;
    private final AdminUserService adminUsers;

    @Transactional
    public String create(String username, CreateFarmRequest request) {
        User actor = users.findByUsernameForUpdate(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "ไม่พบบัญชีผู้ใช้"));

        boolean superAdmin = "Super Admin".equals(actor.getUserRole());
        boolean farmAdmin = "Farm Admin".equals(actor.getUserRole());

        if (!actor.isActive() || (!superAdmin && !farmAdmin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์สร้างฟาร์ม");
        }

        if (farmAdmin && actor.getFarmId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "บัญชีนี้มีฟาร์มแล้ว");
        }

        if (superAdmin && request.accounts().stream()
                .noneMatch(account -> "Farm Admin".equals(account.role()))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "กรุณาสร้างบัญชี Farm Admin อย่างน้อยหนึ่งบัญชี");
        }

        Farm farm = new Farm();
        farm.setFarmId(UUID.randomUUID().toString());
        farm.setFarmName(request.farmName().trim());
        farm.setAddress(request.address().trim());
        farm.setFarmRegistrationNumber(request.farmRegistrationNumber().trim());
        farm.setTaxId(request.taxId().trim());
        farm.setOperationStartDate(request.operationStartDate());
        farm.setOwnerName(request.ownerName().trim());
        farm.setContactPhone(request.contactPhone().trim());
        farms.saveAndFlush(farm);

        if (farmAdmin) {
            actor.setFarmId(farm.getFarmId());
        }

        for (var account : request.accounts()) {
            if ("Super Admin".equals(account.role())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "ไม่สามารถสร้างบัญชี Super Admin จากฟอร์มฟาร์ม");
            }

            adminUsers.create(username, new CreateUserRequest(
                    account.username(),
                    account.temporaryPassword(),
                    account.role(),
                    farm.getFarmId()));
        }

        return farm.getFarmId();
    }
}