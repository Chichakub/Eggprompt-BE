package com.eggprompt.backend.user;

import com.eggprompt.backend.auth.*;
import com.eggprompt.backend.config.SecurityConfig;
import com.eggprompt.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.servlet.Filter;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserTests {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    UserRepository users;
    User actor, target;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminUserController.class, AdminUserService.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean EmailVerificationService emailVerification() { return mock(EmailVerificationService.class); }
        @Bean JwtService jwt() { return new JwtService("", 3600); }
        @Bean RevokedTokenStore revoked() { return new RevokedTokenStore(mock(RevokedTokenRepository.class)); }
    }
    User account(int id, String name, String role, String farm) {
        User u = new User(); u.setUserId(id); u.setUsername(name); u.setUserRole(role); u.setFarmId(farm);
        u.setActive(true); u.setPasswordHash("NEVER_RETURN_HASH"); return u;
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        users = context.getBean(UserRepository.class);
        actor = account(1,"admin","Super Admin",null); target = account(2,"staff.one","Farm Staff","FARM-001");
        when(users.findById(1)).thenReturn(Optional.of(actor));
        when(users.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(actor));
        when(users.findByIdForUpdate(2)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(actor));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }
    @AfterEach void close() { context.close(); }
    String token() { return "Bearer " + context.getBean(JwtService.class).generateToken(actor); }
    String body(String name,String role,boolean active) { return "{\"username\":\""+name+"\",\"role\":\""+role+"\",\"active\":"+active+"}"; }
    @Test void creationRequiresFarmAdminEmailAndNormalizesOptionalStaffEmail() throws Exception {
        String admin="{\"username\":\"farm.admin\",\"role\":\"Farm Admin\",\"password\":\"Strong123!\",\"farmId\":\"FARM-001\"}";
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(admin)).andExpect(status().isBadRequest());
        String staff="{\"username\":\"email.staff\",\"role\":\"Farm Staff\",\"password\":\"Strong123!\",\"farmId\":\"FARM-001\",\"email\":\" Staff@Example.com \"}";
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(staff)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("staff@example.com")).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.mustChangePassword").value(true));
        verify(context.getBean(EmailVerificationService.class)).issue(argThat(u->"staff@example.com".equals(u.getEmail())));
        when(users.existsByEmailNormalized("staff@example.com")).thenReturn(true);
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(staff)).andExpect(status().isConflict());
    }
    @Test void temporaryPasswordRevokesOldPasswordAndEveryOldToken() throws Exception {
        actor.setUserRole("Farm Admin");actor.setFarmId("FARM-001");
        var encoder=context.getBean(PasswordEncoder.class);target.setPasswordHash(encoder.encode("OldStrong123!"));
        when(users.findById(2)).thenReturn(Optional.of(target));
        String oldToken="Bearer "+context.getBean(JwtService.class).generateToken(target);
        var response=mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String issued=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("temporaryPassword").asText();
        assertTrue(encoder.matches(issued,target.getPasswordHash()));assertFalse(encoder.matches("OldStrong123!",target.getPasswordHash()));
        assertEquals(1,target.getAuthVersion());assertTrue(target.getMustChangePassword());
        mvc.perform(get("/v1/auth/me").header("Authorization",oldToken)).andExpect(status().isUnauthorized());
        target.setMustChangePassword(false);
        mvc.perform(get("/v1/auth/me").header("Authorization",oldToken)).andExpect(status().isUnauthorized());
        // A second reissue also invalidates an earlier temporary token.
        target.setMustChangePassword(true);String firstTemporary="Bearer "+context.getBean(JwtService.class).generateToken(target);
        mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token())).andExpect(status().isOk());
        mvc.perform(post("/v1/auth/change-password").header("Authorization",firstTemporary)).andExpect(status().isUnauthorized());
    }
    @Test void reissueRejectsCrossFarmEmailAccountsAndOtherRoles() throws Exception {
        actor.setUserRole("Farm Admin");actor.setFarmId("FARM-001");target.setFarmId("FARM-002");
        mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token())).andExpect(status().isForbidden());
        target.setFarmId("FARM-001");target.setEmail("staff@example.com");
        mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token())).andExpect(status().isBadRequest());
        target.setEmail(null);target.setUserRole("Farm Admin");
        mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token())).andExpect(status().isForbidden());
        actor.setUserRole("Super Admin");
        mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token())).andExpect(status().isForbidden());
    }
    @Test void financeStaffWithoutEmailCanReceiveTemporaryPassword() throws Exception {
        actor.setUserRole("Farm Admin");actor.setFarmId("FARM-001");target.setUserRole("Finance Staff");
        mvc.perform(post("/v1/admin/users/2/temporary-password").header("Authorization",token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isString());
        assertTrue(target.getMustChangePassword());
    }
    @Test void invalidEmailRejectedAndBlankEmailIsOptionalForStaff() throws Exception {
        String payload="{\"username\":\"test.staff\",\"role\":\"Farm Staff\",\"password\":\"Strong123!\",\"farmId\":\"FARM-001\",\"email\":\"bad-email\"}";
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(payload.replace("bad-email","   "))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.mustChangePassword").value(true));
    }
    @Test void addFarmAndUserManagementShareVerificationRules() {
        var service=context.getBean(AdminUserService.class);
        service.createForFarm("admin",new AdminUserController.CreateUserRequest("farm.admin","Strong123!","Farm Admin","FARM-001")," Admin@Example.com ");
        verify(context.getBean(EmailVerificationService.class)).issue(argThat(u->"admin@example.com".equals(u.getEmail()) && Boolean.TRUE.equals(u.getMustChangePassword()) && !u.isActive()));
    }
    @Test void pendingVerificationCannotBeBypassedWithStatusToggle() throws Exception {
        target.setEmailVerificationHash("pending");
        mvc.perform(put("/v1/admin/users/2").header("Authorization",token()).contentType("application/json").content(body("staff.one","Farm Staff",true))).andExpect(status().isBadRequest());
    }
    @Test void anonymousAndFinanceDenied() throws Exception {
        mvc.perform(get("/v1/admin/users")).andExpect(status().isUnauthorized());
        actor.setUserRole("Finance Staff");
        mvc.perform(get("/v1/admin/users").header("Authorization",token())).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/users/2").header("Authorization",token())).andExpect(status().isForbidden());
    }
    @Test void listPagedAndNeverExposesPassword() throws Exception {
        when(users.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(target),PageRequest.of(0,10),1));
        mvc.perform(get("/v1/admin/users").param("search","staff").header("Authorization",token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].username").value("staff.one"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void createHashesPasswordAndRejectsDuplicate() throws Exception {
        String payload="{\"username\":\"new.staff\",\"role\":\"Farm Staff\",\"password\":\"Strong123!\",\"farmId\":\"FARM-001\"}";
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(payload)).andExpect(status().isCreated());
        var capture=org.mockito.ArgumentCaptor.forClass(User.class); verify(users).saveAndFlush(capture.capture());
        assertTrue(context.getBean(PasswordEncoder.class).matches("Strong123!",capture.getValue().getPasswordHash()));
        assertTrue(capture.getValue().getMustChangePassword());
        assertTrue(capture.getValue().isActive());
        when(users.existsByUsername("new.staff")).thenReturn(true);
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json").content(payload)).andExpect(status().isConflict());
    }
    @Test void invalidPasswordAndPaginationRejected() throws Exception {
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json")
                .content("{\"username\":\"new.staff\",\"role\":\"Farm Staff\",\"password\":\"weak\",\"farmId\":\"FARM-001\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/users").param("size","0").header("Authorization",token())).andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/users/2").header("Authorization",token()).contentType("application/json").content(body("staff.one","Unknown",true))).andExpect(status().isBadRequest());
    }
    @Test void updateRoleStatusAndSoftDelete() throws Exception {
        mvc.perform(put("/v1/admin/users/2").header("Authorization",token()).contentType("application/json").content(body("staff.one","Finance Staff",false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Finance Staff")).andExpect(jsonPath("$.active").value(false));
        mvc.perform(delete("/v1/admin/users/2").header("Authorization",token())).andExpect(status().isNoContent());
        assertNotNull(target.getDeletedAt()); assertFalse(target.isActive()); verify(users,never()).delete(any(User.class));
    }
    @Test void cannotDeleteOrDisableSelf() throws Exception {
        mvc.perform(delete("/v1/admin/users/1").header("Authorization",token())).andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/users/1").header("Authorization",token()).contentType("application/json").content(body("admin","Super Admin",false))).andExpect(status().isBadRequest());
    }
    @Test void farmAdminCannotCrossFarmOrPromoteStaff() throws Exception {
        actor.setUserRole("Farm Admin"); actor.setFarmId("FARM-001"); target.setFarmId("FARM-002");
        mvc.perform(delete("/v1/admin/users/2").header("Authorization",token())).andExpect(status().isForbidden());
        target.setFarmId("FARM-001");
        mvc.perform(put("/v1/admin/users/2").header("Authorization",token()).contentType("application/json").content(body("staff.one","Super Admin",true))).andExpect(status().isForbidden());
        mvc.perform(put("/v1/admin/users/2").header("Authorization",token()).contentType("application/json").content(body("staff.one","Finance Staff",true))).andExpect(status().isOk());
    }
    @Test void farmAdminCreationUsesOwnFarm() throws Exception {
        actor.setUserRole("Farm Admin"); actor.setFarmId("FARM-001");
        mvc.perform(post("/v1/admin/users").header("Authorization",token()).contentType("application/json")
                .content("{\"username\":\"new.staff\",\"role\":\"Farm Staff\",\"password\":\"Strong123!\",\"farmId\":\"FARM-OTHER\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value("FARM-001"));
    }
    @Test void farmHeaderCannotOverrideAssignedFarm() throws Exception {
        actor.setUserRole("Farm Admin");
        actor.setFarmId("FARM-001");
        mvc.perform(get("/v1/admin/users").header("Authorization", token()).header("X-Farm-Id", "FARM-002"))
                .andExpect(status().isForbidden());
        verify(users, never()).findAll(any(Specification.class), any(Pageable.class));
    }
    @Test void superAdminCanManageUsersWithinSelectedFarm() throws Exception {
        mvc.perform(delete("/v1/admin/users/2").header("Authorization", token()).header("X-Farm-Id", "FARM-002"))
                .andExpect(status().isForbidden());
        assertNull(target.getDeletedAt());
        mvc.perform(delete("/v1/admin/users/2").header("Authorization", token()).header("X-Farm-Id", "FARM-001"))
                .andExpect(status().isNoContent());
        assertNotNull(target.getDeletedAt());
    }
}
