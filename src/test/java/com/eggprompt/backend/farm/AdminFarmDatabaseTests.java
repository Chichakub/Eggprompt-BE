package com.eggprompt.backend.farm;

import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="EGGPROMPT_DATABASE_TEST",matches="true")
class AdminFarmDatabaseTests {
    @Autowired FarmRepository farms;
    @Autowired UserRepository users;
    @Autowired AdminFarmService service;
    @Autowired AdminUserService adminUsers;
    @Test void realFarmSearchCountsStatusAndUserScope() {
        String key="qa"+UUID.randomUUID().toString().substring(0,8);
        User actor=new User();actor.setUsername(key);actor.setUserRole("Super Admin");actor.setActive(true);actor.setPasswordHash("test-only");users.saveAndFlush(actor);
        Farm farm=new Farm();farm.setFarmId(key);farm.setFarmName(key+" Farm");farm.setOwnerName("Test owner");
        farm.setTaxId("1234567890123");farm.setAddress("Test address");farm.setOperationStartDate(LocalDate.now());farm.setContactPhone("0800000000");
        farms.saveAndFlush(farm);
        User staff=new User();staff.setUsername(key+"staff");staff.setUserRole("Farm Staff");staff.setFarmId(key);staff.setActive(true);staff.setPasswordHash("test-only");users.saveAndFlush(staff);
        assertEquals(1,service.list(key,key,"active",0,10).totalElements());
        assertEquals(1,service.detail(key,key).userCount());
        assertEquals(1,adminUsers.list(key,"","",0,10,key).totalElements());
        assertNotNull(service.summary(key).usersByRole());
        farm.setActive(false);farms.saveAndFlush(farm);
        assertEquals(0,service.list(key,key,"active",0,10).totalElements());
        assertEquals(1,service.list(key,key,"suspended",0,10).totalElements());
        farm.setDeletedAt(Instant.now());farms.saveAndFlush(farm);
        assertEquals(0,service.list(key,key,"all",0,10).totalElements());
    }
}
