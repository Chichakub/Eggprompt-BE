package com.eggprompt.backend.egg;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EggProductionTests {
    JdbcTemplate jdbc; UserRepository users; EggProductionService service; User actor;
    EggProductionController.Request request(int good,int defective) {
        return new EggProductionController.Request(1,LocalDate.now(),0,good,0,0,0,0,0,defective,"LOT-01");
    }
    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class);users=mock(UserRepository.class);service=new EggProductionService(jdbc,users);
        actor=new User();actor.setUserId(5);actor.setActive(true);actor.setFarmId("F1");actor.setUserRole("Farm Staff");
        when(users.findByUsernameAndDeletedAtIsNull("staff")).thenReturn(Optional.of(actor));
        when(jdbc.queryForList(anyString(),eq(1))).thenReturn(List.of(Map.of("farm_id","F1","received_date",java.sql.Date.valueOf(LocalDate.now().minusDays(1)))));
    }
    @Test void totalsIncludeDefectiveAndRoundTraysAndPercentage() {
        var summary=EggProductionService.summarize(request(98,2));
        assertEquals(98,summary.saleableEggCount());assertEquals(100,summary.totalEggCount());
        assertEquals(new BigDecimal("3.27"),summary.totalTrayCount());assertEquals(new BigDecimal("2.00"),summary.defectivePercent());
        assertEquals(new BigDecimal("0.00"),EggProductionService.summarize(request(0,0)).defectivePercent());
    }
    @Test void staffCanRecordAndAuthorComesFromAuthenticatedUser() {
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(17L);
        var result=service.create("staff",request(98,2),null);
        assertEquals(17,result.eggLogId());assertEquals(100,result.summary().totalEggCount());
        verify(jdbc).queryForObject(contains("INSERT INTO"),eq(Long.class),eq(1),any(LocalDate.class),eq(0),eq(98),eq(0),eq(0),eq(0),eq(0),eq(0),eq(2),eq("LOT-01"),eq(5),eq("DAILY"));
    }
    @Test void duplicateDayReturnsConflict() {
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(1),any(LocalDate.class),eq("DAILY"),eq("DAILY"),eq(-1L))).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT,assertThrows(ApiException.class,()->service.create("staff",request(1,0),null)).getStatus());
    }
    @Test void crossFarmAndSelectedFarmMismatchAreForbidden() {
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.create("staff",request(1,0),"F2")).getStatus());
        actor.setFarmId("F2");
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.create("staff",request(1,0),null)).getStatus());
    }
    @Test void financeCannotRecord() {
        actor.setUserRole("Finance Staff");
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.create("staff",request(1,0),null)).getStatus());
        verifyNoInteractions(jdbc);
    }
    @Test void overviewUsesWeightedRatio() {
        var days=List.of(
            new EggProductionService.DailySummary(LocalDate.now().minusDays(1),EggProductionService.totals(90,10)),
            new EggProductionService.DailySummary(LocalDate.now(),EggProductionService.totals(900,0)));
        doReturn(days).when(jdbc).query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class));
        var result=service.overview("staff",1,null,null,null);
        assertEquals(2,result.recordedDays());assertEquals(1000,result.summary().totalEggCount());
        assertEquals(new BigDecimal("1.00"),result.summary().defectivePercent());
        assertEquals(new BigDecimal("33.00"),result.summary().totalTrayCount());
    }
    @Test void emptyOverviewAndInvalidScopeOrDates() {
        doReturn(List.of()).when(jdbc).query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class));
        var result=service.overview("staff",1,null,null,null);
        assertEquals(0,result.recordedDays());assertEquals(new BigDecimal("0.00"),result.summary().defectivePercent());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.overview("staff",1,null,null,"F2")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ApiException.class,()->service.overview("staff",1,LocalDate.now(),LocalDate.now().minusDays(1),null)).getStatus());
    }
    @Test void staffCannotEditOrDeleteEvenThroughService() {
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.update("staff",1,request(1,0),null)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.delete("staff",1,null)).getStatus());
        verifyNoInteractions(jdbc);
    }
    @Test void adminDeleteSoftDeletesOnlyWithinOwnFarm() {
        actor.setUserRole("Farm Admin");
        when(jdbc.queryForList(anyString(),eq(9L))).thenReturn(List.of(Map.of("farm_id","F1")));
        service.delete("staff",9,null);
        verify(jdbc).update(contains("deleted_at=CURRENT_TIMESTAMP"),eq(9L));
        when(jdbc.queryForList(anyString(),eq(10L))).thenReturn(List.of(Map.of("farm_id","F2")));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.delete("staff",10,null)).getStatus());
    }
    @Test void adminCanEditCountsWhileRetainingOriginalRecorderAndFlock() {
        actor.setUserRole("Farm Admin");
        when(jdbc.queryForList(contains("SELECT flock_id,record_date"),eq(9L))).thenReturn(List.of(Map.of("flock_id",1,"record_date",java.sql.Date.valueOf(LocalDate.now()))));
        when(jdbc.queryForList(contains("FOR UPDATE OF e"),eq(9L))).thenReturn(List.of(Map.of("farm_id","F1")));
        var result=service.update("staff",9,request(98,2),null);
        assertEquals(100,result.summary().totalEggCount());
        verify(jdbc).update(contains("UPDATE public.daily_egg_logs"),eq(0),eq(98),eq(0),eq(0),eq(0),eq(0),eq(0),eq(2),eq("LOT-01"),eq("DAILY"),eq(9L));
    }
    @Test void morningAndAfternoonUseSeparateRoundKeys() {
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(17L);
        var r=new EggProductionController.Request(1,LocalDate.now(),0,1,0,0,0,0,0,0,null,"MORNING");
        service.create("staff",r,null);
        verify(jdbc).queryForObject(contains("SELECT EXISTS"),eq(Boolean.class),eq(1),any(LocalDate.class),eq("MORNING"),eq("MORNING"),eq(-1L));
        var afternoon=new EggProductionController.Request(1,LocalDate.now(),0,1,0,0,0,0,0,0,null,"AFTERNOON");
        service.create("staff",afternoon,null);
        verify(jdbc).queryForObject(contains("SELECT EXISTS"),eq(Boolean.class),eq(1),any(LocalDate.class),eq("AFTERNOON"),eq("AFTERNOON"),eq(-1L));
    }
    @Test void defectiveEggsDoNotIncreaseSaleableTrays() {
        assertEquals(EggProductionService.totals(160,0).totalTrayCount(),EggProductionService.totals(160,50).totalTrayCount());
        assertEquals(new BigDecimal("0.00"),EggProductionService.totals(0,50).totalTrayCount());
    }
    @Test void futureDatesAreRejected() {
        var r=new EggProductionController.Request(1,LocalDate.now().plusDays(2),0,1,0,0,0,0,0,0,null);
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ApiException.class,()->service.create("staff",r,null)).getStatus());
    }
}
