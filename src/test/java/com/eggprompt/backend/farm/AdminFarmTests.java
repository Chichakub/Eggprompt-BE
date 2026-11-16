package com.eggprompt.backend.farm;

import com.eggprompt.backend.auth.*;
import com.eggprompt.backend.config.SecurityConfig;
import com.eggprompt.backend.exception.GlobalExceptionHandler;
import com.eggprompt.backend.house.HouseRepository;
import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.servlet.Filter;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminFarmTests {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    User actor;
    Farm farm;
    FarmRepository farms;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,JwtAuthenticationFilter.class,AdminFarmController.class,AdminFarmService.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean FarmRepository farms() { return mock(FarmRepository.class); }
        @Bean HouseRepository houses() { return mock(HouseRepository.class); }
        @Bean JwtService jwt() { return new JwtService("",3600); }
        @Bean RevokedTokenStore revoked() { return new RevokedTokenStore(mock(RevokedTokenRepository.class)); }
    }
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
        var users=context.getBean(UserRepository.class);
        actor=new User();actor.setUserId(1);actor.setUsername("super");actor.setUserRole("Super Admin");actor.setActive(true);
        when(users.findById(1)).thenReturn(Optional.of(actor));
        when(users.findByUsernameAndDeletedAtIsNull("super")).thenReturn(Optional.of(actor));
        farms=context.getBean(FarmRepository.class);farm=new Farm();farm.setFarmId("F1");farm.setFarmName("First farm");farm.setOwnerName("Owner");
        when(farms.findById("F1")).thenReturn(Optional.of(farm));
        when(farms.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(farm),PageRequest.of(0,10),1));
        when(users.findByFarmIdAndUserRoleAndDeletedAtIsNullOrderByUserIdAsc(anyString(),anyString())).thenReturn(List.of());
        when(context.getBean(HouseRepository.class).countByFarmIdAndDeletedAtIsNull("F1")).thenReturn(3L);
    }
    @AfterEach void close() { context.close(); }
    String token() { return "Bearer "+context.getBean(JwtService.class).generateToken(actor); }
    @Test void onlySuperAdminMayInspectOrReadSummary() throws Exception {
        mvc.perform(get("/v1/admin/farms")).andExpect(status().isUnauthorized());
        for(String role:List.of("Farm Admin","Farm Staff","Finance Staff")) {
            actor.setUserRole(role);
            mvc.perform(get("/v1/admin/farms").header("Authorization",token())).andExpect(status().isForbidden());
            mvc.perform(get("/v1/admin/farms/F1").header("Authorization",token())).andExpect(status().isForbidden());
            mvc.perform(get("/v1/admin/farms/summary").header("Authorization",token())).andExpect(status().isForbidden());
        }
    }
    @Test void listAndDetailIncludeCountsWithoutCredentials() throws Exception {
        mvc.perform(get("/v1/admin/farms").param("search","farm").param("status","active").header("Authorization",token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].farmName").value("First farm"))
                .andExpect(jsonPath("$.content[0].houseCount").value(3)).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/v1/admin/farms/F1").header("Authorization",token())).andExpect(status().isOk()).andExpect(jsonPath("$.farmId").value("F1"));
    }
    @Test void summaryAndInputValidation() throws Exception {
        mvc.perform(get("/v1/admin/farms/summary").header("Authorization",token())).andExpect(status().isOk()).andExpect(jsonPath("$.usersByRole").exists());
        mvc.perform(get("/v1/admin/farms").param("page","-1").header("Authorization",token())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/farms").param("status","invalid").header("Authorization",token())).andExpect(status().isBadRequest());
    }
    @Test void missingAndDeletedFarmsCannotBeInspected() throws Exception {
        mvc.perform(get("/v1/admin/farms/missing").header("Authorization",token())).andExpect(status().isNotFound());
        farm.setDeletedAt(java.time.Instant.now());
        mvc.perform(get("/v1/admin/farms/F1").header("Authorization",token())).andExpect(status().isNotFound());
    }
}
