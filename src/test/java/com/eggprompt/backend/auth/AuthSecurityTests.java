package com.eggprompt.backend.auth;

import com.eggprompt.backend.config.SecurityConfig;
import com.eggprompt.backend.user.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.Filter;
import java.util.Optional;
import java.util.HashSet;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSecurityTests {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    UserRepository users;
    JwtService jwt;
    User user;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthController.class, AuthService.class, AdminEndpoint.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JwtService jwt() { return new JwtService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 3600); }
        @Bean RevokedTokenStore revoked() {
            var repository = mock(RevokedTokenRepository.class);
            var hashes = new HashSet<String>();
            when(repository.existsById(anyString())).thenAnswer(i -> hashes.contains(i.getArgument(0)));
            // Store uses a stable hash; capture the entity ID via reflection for this repository fake.
            when(repository.save(any())).thenAnswer(i -> {
                RevokedToken entity = i.getArgument(0);
                var field = RevokedToken.class.getDeclaredField("tokenHash");
                field.setAccessible(true);
                hashes.add((String) field.get(entity));
                return entity;
            });
            return new RevokedTokenStore(repository);
        }
    }
    @RestController
    static class AdminEndpoint {
        @GetMapping("/v1/users/test") String users() { return "ok"; }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        users = context.getBean(UserRepository.class);
        jwt = context.getBean(JwtService.class);
        user = new User();
        user.setUserId(7); user.setUsername("tester"); user.setUserRole("Farm Admin"); user.setActive(true);
        user.setPasswordHash(context.getBean(PasswordEncoder.class).encode("secret"));
        when(users.findById(7)).thenReturn(Optional.of(user));
        when(users.findByUsernameAndDeletedAtIsNull("tester")).thenReturn(Optional.of(user));
    }
    @AfterEach void close() { context.close(); }
    String bearer() { return "Bearer " + jwt.generateToken(user); }

    @Test void profileRequiresToken() throws Exception {
        mvc.perform(get("/v1/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void profileUsesUsernameFromUserIdAndReturnsDirectObject() throws Exception {
        mvc.perform(get("/v1/auth/me").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.role").value("Farm Admin"));
    }
    @Test void invalidExpiredAndInactiveTokensAreRejected() throws Exception {
        mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        String expired = new JwtService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", -1).generateToken(user);
        mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        user.setActive(false);
        mvc.perform(get("/v1/auth/me").header("Authorization", bearer())).andExpect(status().isUnauthorized());
    }
    @Test void deletedAndUnknownRoleUsersAreRejected() throws Exception {
        user.setDeletedAt(java.time.Instant.now());
        mvc.perform(get("/v1/auth/me").header("Authorization", bearer())).andExpect(status().isUnauthorized());
        user.setDeletedAt(null);
        user.setUserRole("unknown");
        mvc.perform(get("/v1/auth/me").header("Authorization", bearer())).andExpect(status().isUnauthorized());
    }
    @Test void staffCannotCallAdminApi() throws Exception {
        user.setUserRole("Farm Staff");
        mvc.perform(get("/v1/users/test").header("Authorization", bearer())).andExpect(status().isForbidden());
        user.setUserRole("Farm Admin");
        mvc.perform(get("/v1/users/test").header("Authorization", bearer())).andExpect(status().isOk());
    }
    @Test void logoutRevokesOnlyThatSession() throws Exception {
        String token = bearer();
        String another = bearer();
        mvc.perform(post("/v1/auth/logout").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/v1/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/auth/me").header("Authorization", another)).andExpect(status().isOk());
    }
    @Test void loginIsPublicAndReturnsToken() throws Exception {
        mvc.perform(post("/v1/auth/login").header("Authorization", "Bearer stale-token").contentType("application/json")
                .content("{\"username\":\"tester\",\"password\":\"secret\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
