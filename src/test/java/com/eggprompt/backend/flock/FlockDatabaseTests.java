package com.eggprompt.backend.flock;

import com.eggprompt.backend.farm.*;
import com.eggprompt.backend.house.*;
import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="EGGPROMPT_DATABASE_TEST",matches="true")
class FlockDatabaseTests {
    @Autowired JdbcTemplate jdbc; @Autowired FarmRepository farms; @Autowired HouseRepository houses;
    @Autowired UserRepository users; @Autowired FlockService service;
    @Test void crudAndSetupCompletionAgainstPostgres() {
        String key="qa"+UUID.randomUUID().toString().substring(0,8);
        Farm farm=new Farm();farm.setFarmId(key);farm.setFarmName(key);farm.setOwnerName("QA");farm.setTaxId("1234567890123");farm.setAddress("Test");farm.setOperationStartDate(LocalDate.now());farm.setContactPhone("0800000000");farms.saveAndFlush(farm);
        House house=new House();house.setFarmId(key);house.setHouseName(key);house.setHouseType("CLOSED");house.setOpeningDate(LocalDate.now());house.setWidthM(BigDecimal.TEN);house.setLengthM(BigDecimal.TEN);house.setCapacity(500);houses.saveAndFlush(house);
        User actor=new User();actor.setUsername(key);actor.setUserRole("Farm Admin");actor.setFarmId(key);actor.setActive(true);actor.setPasswordHash("test-only");actor.setOnboardingRequired(true);users.saveAndFlush(actor);
        int breed=jdbc.query("SELECT breed_id FROM breeds WHERE is_active AND deleted_at IS NULL AND (breed_code='HYL-BRN' OR lower(trim(breed_name))='hy-line brown') ORDER BY breed_id LIMIT 1",(r,i)->r.getInt(1)).stream().findFirst()
                .orElseGet(()->jdbc.queryForObject("INSERT INTO breeds(breed_name,breed_code) VALUES('Hy-Line Brown','HYL-BRN') RETURNING breed_id",Integer.class));
        var request=new FlockController.Request(key,house.getHouseId(),breed,LocalDate.now(),10,100,"Supplier",null,BigDecimal.TEN,BigDecimal.TEN,true);
        var created=service.create(key,request);assertFalse(actor.isOnboardingRequired());
        assertEquals(1,service.list(key,"OTHER-FARM").size());assertEquals(100,created.currentCount());
        assertEquals(100,service.update(key,created.flockId(),request).currentCount());
        service.delete(key,created.flockId());assertTrue(service.list(key,null).isEmpty());
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM flocks WHERE flock_id=?",java.sql.Timestamp.class,created.flockId()));
    }
}
