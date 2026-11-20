package com.eggprompt.backend.egg;

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

class EggProductionRoleApiTests {
    AnnotationConfigWebApplicationContext context;MockMvc mvc;User actor;EggProductionService service;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,JwtAuthenticationFilter.class,EggProductionController.class})
    static class Config {
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean EggProductionService service(){return mock(EggProductionService.class);}
        @Bean JwtService jwt(){return new JwtService("",3600);}
        @Bean RevokedTokenStore revoked(){return new RevokedTokenStore(mock(RevokedTokenRepository.class));}
    }
    @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
        actor=new User();actor.setUserId(1);actor.setUsername("staff");actor.setUserRole("Farm Staff");actor.setFarmId("F1");actor.setActive(true);
        when(context.getBean(UserRepository.class).findById(1)).thenReturn(Optional.of(actor));service=context.getBean(EggProductionService.class);}
    @AfterEach void close(){context.close();}
    String token(){return "Bearer "+context.getBean(JwtService.class).generateToken(actor);}
    String payload(){return """
      {"flockId":1,"recordDate":"2026-01-01","gradeJumboQty":0,"grade0Qty":100,
       "grade1Qty":0,"grade2Qty":0,"grade3Qty":0,"grade4Qty":0,"grade5Qty":0,"defectiveQty":1,"collectionRound":"MORNING"}
      """;}
    @Test void staffCanAddAndReadButCannotEditOrDelete() throws Exception {
        mvc.perform(post("/v1/egg-production").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isCreated());
        mvc.perform(get("/v1/egg-production").header("Authorization",token())).andExpect(status().isOk());
        mvc.perform(put("/v1/egg-production/7").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/egg-production/7").header("Authorization",token())).andExpect(status().isForbidden());
        verify(service,never()).update(anyString(),anyLong(),any(),any());
        verify(service,never()).delete(anyString(),anyLong(),any());
    }
    @Test void adminCanEditAndDelete() throws Exception {
        actor.setUserRole("Farm Admin");
        mvc.perform(put("/v1/egg-production/7").header("Authorization",token()).contentType("application/json").content(payload())).andExpect(status().isOk());
        mvc.perform(delete("/v1/egg-production/7").header("Authorization",token())).andExpect(status().isNoContent());
        verify(service).update(eq("staff"),eq(7L),any(),isNull());
        verify(service).delete("staff",7L,null);
    }
}
