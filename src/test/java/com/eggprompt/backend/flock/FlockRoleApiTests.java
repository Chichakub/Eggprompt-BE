package com.eggprompt.backend.flock;

import com.eggprompt.backend.auth.*;
import com.eggprompt.backend.config.SecurityConfig;
import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.Filter;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FlockRoleApiTests {
    AnnotationConfigWebApplicationContext context;MockMvc mvc;User actor;FlockService service;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,JwtAuthenticationFilter.class,FlockController.class})
    static class Config {
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean FlockService service(){return mock(FlockService.class);}
        @Bean JwtService jwt(){return new JwtService("",3600);}
        @Bean RevokedTokenStore revoked(){return new RevokedTokenStore(mock(RevokedTokenRepository.class));}
    }
    @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
        actor=new User();actor.setUserId(1);actor.setUsername("staff");actor.setUserRole("Farm Staff");actor.setFarmId("F1");actor.setActive(true);
        when(context.getBean(UserRepository.class).findById(1)).thenReturn(Optional.of(actor));service=context.getBean(FlockService.class);}
    @AfterEach void close(){context.close();}
    String token(){return "Bearer "+context.getBean(JwtService.class).generateToken(actor);}
    String payload(){return "{\"flockCode\":\"F70-01\",\"houseId\":1,\"breedId\":1,\"receivedDate\":\"2026-01-01\",\"initialAgeWeeks\":10,\"initialCount\":100,\"supplier\":\"Supplier\",\"pricePerBird\":100,\"initialAverageWeightG\":1000,\"vaccinated\":true}";}
    @Test void staffMayCreateButUpdateAndDeleteAreForbidden() throws Exception {
        mvc.perform(post("/v1/flocks").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isCreated());
        verify(service).create(eq("staff"),any());
        mvc.perform(put("/v1/flocks/7").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isForbidden());
        verify(service,never()).update(anyString(),anyInt(),any());
        mvc.perform(delete("/v1/flocks/7").header("Authorization",token())).andExpect(status().isForbidden());verify(service,never()).delete(anyString(),anyInt());
    }
    @Test void scopedCreatePassesAuthenticatedFarmToService() throws Exception {
        mvc.perform(post("/v1/flocks").header("Authorization",token()).header("X-Farm-Id","F1")
                .contentType("application/json").content(payload())).andExpect(status().isCreated());
        verify(service).create(eq("staff"),any(),eq("F1"));
        verify(service,never()).create(anyString(),any());
    }
    @Test void superAdminScopeSwitchesOnEveryRequest() throws Exception {
        actor.setUserRole("Super Admin");
        for (String farm : new String[]{"F1", "F2", "F1"}) {
            mvc.perform(post("/v1/flocks").header("Authorization",token()).header("X-Farm-Id",farm)
                    .contentType("application/json").content(payload())).andExpect(status().isCreated());
        }
        verify(service,times(2)).create(eq("staff"),any(),eq("F1"));
        verify(service).create(eq("staff"),any(),eq("F2"));
    }
    @Test void rejectsSpoofedAndMalformedFarmHeaders() throws Exception {
        mvc.perform(get("/v1/flocks").header("Authorization",token()).header("X-Farm-Id","F2"))
                .andExpect(status().isForbidden());
        for (String farm : new String[]{" ", "F1,F2", "123456789012345678901"}) {
            mvc.perform(get("/v1/flocks").header("Authorization",token()).header("X-Farm-Id",farm))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/v1/flocks").header("Authorization",token()).header("X-Farm-Id","F1","F2"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void financeCannotCreateAndAdminMayStillDelete() throws Exception {
        actor.setUserRole("Finance Staff");
        mvc.perform(post("/v1/flocks").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isForbidden());
        actor.setUserRole("Farm Admin");
        mvc.perform(put("/v1/flocks/7").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isOk());
        verify(service).update(eq("staff"),eq(7),any());
        mvc.perform(delete("/v1/flocks/7").header("Authorization",token())).andExpect(status().isNoContent());
    }
}
