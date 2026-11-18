package com.eggprompt.backend.flock;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.farm.FarmSetupController;
import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlockSecurityTests {
    UserRepository users;
    JdbcTemplate jdbc;
    FlockService service;
    User actor;
    @BeforeEach void setup() {
        users=mock(UserRepository.class);jdbc=mock(JdbcTemplate.class);service=new FlockService(jdbc,users);
        actor=new User();actor.setUserId(1);actor.setUsername("tester");actor.setUserRole("Farm Admin");actor.setFarmId("F1");actor.setActive(true);
        when(users.findByUsernameAndDeletedAtIsNull("tester")).thenReturn(Optional.of(actor));
    }
    @Test void staffCannotDelete() {
        actor.setUserRole("Farm Staff");
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.delete("tester",7)).getStatus());
        verifyNoInteractions(jdbc);
    }
    @Test void staffCannotCreateInAnotherFarm() {
        actor.setUserRole("Farm Staff");
        when(jdbc.queryForList(anyString(),eq(2))).thenReturn(List.of(Map.of("farm_id","F2","capacity",1000,"status","ACTIVE")));
        var request=new FlockController.Request("F70-01",2,1,java.time.LocalDate.now(),10,100,"Supplier",null,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,true);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.create("tester",request)).getStatus());
    }
    @Test void scopedCreateCannotUseHouseOutsideSelectedFarm() {
        actor.setUserRole("Super Admin");
        when(jdbc.queryForList(anyString(),eq(2))).thenReturn(List.of(Map.of("farm_id","F2")));
        var request=new FlockController.Request("F70-01",2,1,java.time.LocalDate.now(),10,100,"Supplier",null,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,true);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.create("tester",request,"F1")).getStatus());
        verify(jdbc,never()).queryForObject(anyString(),eq(Integer.class),any(Object[].class));
    }
    @Test void financeAndInactiveAccountsCannotRead() {
        actor.setUserRole("Finance Staff");
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.list("tester",null)).getStatus());
        actor.setActive(false);
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ApiException.class,()->service.list("tester",null)).getStatus());
        verifyNoInteractions(jdbc);
    }
    @Test void staffWithoutFarmReceivesNoCrossFarmData() {
        actor.setUserRole("Farm Staff");actor.setFarmId(null);
        assertTrue(service.list("tester","OTHER-FARM").isEmpty());verifyNoInteractions(jdbc);
    }
    @Test void existingAccountWithFarmDoesNotRequireSetup() {
        actor.setOnboardingRequired(false);
        var status=new FarmSetupController(users,jdbc).status(new UsernamePasswordAuthenticationToken("tester",null));
        assertEquals(0,status.step());assertEquals("F1",status.farmId());verifyNoInteractions(jdbc);
    }
    @Test void newAccountResumesAllThreeSetupSteps() {
        var controller=new FarmSetupController(users,jdbc);
        var auth=new UsernamePasswordAuthenticationToken("tester",null);
        actor.setOnboardingRequired(true);actor.setFarmId(null);
        assertEquals(1,controller.status(auth).step());
        actor.setFarmId("F1");
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq("F1"))).thenReturn(false);
        assertEquals(2,controller.status(auth).step());
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq("F1"))).thenReturn(true);
        assertEquals(3,controller.status(auth).step());
    }
}
