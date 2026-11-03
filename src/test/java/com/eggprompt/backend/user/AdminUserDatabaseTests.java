package com.eggprompt.backend.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="EGGPROMPT_DATABASE_TEST", matches="true")
class AdminUserDatabaseTests {
    @Autowired UserRepository users;
    @Autowired AdminUserService service;
    @Test void realDatabaseSearchPaginationUpdateAndSoftDelete() {
        String prefix="qa_"+UUID.randomUUID().toString().substring(0,8);
        User actor=new User();actor.setUsername(prefix+"_admin");actor.setUserRole("Super Admin");
        actor.setPasswordHash("test-only");actor.setActive(true);users.saveAndFlush(actor);
        var created=service.create(actor.getUsername(),new AdminUserController.CreateUserRequest(prefix+"_new","Strong123!","Super Admin",null));
        var page=service.list(actor.getUsername(),prefix,"Super Admin",0,1);
        assertEquals(2,page.totalElements());assertEquals(2,page.totalPages());assertEquals(1,page.content().size());
        assertEquals(1,service.list(actor.getUsername(),prefix+"_new","",0,10).totalElements());
        service.update(actor.getUsername(),created.userId(),new AdminUserController.UpdateUserRequest(prefix+"_edit","Super Admin",false));
        assertFalse(users.findById(created.userId()).orElseThrow().isActive());
        service.delete(actor.getUsername(),created.userId());
        assertEquals(1,service.list(actor.getUsername(),prefix,"",0,10).totalElements());
        assertNotNull(users.findById(created.userId()).orElseThrow().getDeletedAt());
        actor.setUserRole("Farm Admin");actor.setFarmId(null);users.saveAndFlush(actor);
        assertEquals(0,service.list(actor.getUsername(),"","",0,10).totalElements());
    }
}
