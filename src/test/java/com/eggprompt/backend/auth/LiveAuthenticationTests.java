package com.eggprompt.backend.auth;

import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in smoke test against the running Vite proxy and actual PostgreSQL. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EGGPROMPT_LIVE_AUTH_TEST", matches = "true")
class LiveAuthenticationTests {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test void loginProfileLogoutThroughLiveFrontendProxy() throws Exception {
        // Remove only disposable accounts left by an interrupted run of this smoke test.
        users.findAll().stream().filter(u -> u.getUsername().startsWith("auth_smoke_"))
                .forEach(u -> users.deleteById(u.getUserId()));
        var user = new User();
        user.setUsername("auth_smoke_" + UUID.randomUUID().toString().substring(0, 8));
        String password = UUID.randomUUID().toString();
        user.setPasswordHash(encoder.encode(password));
        user.setUserRole("Super Admin");
        user.setActive(true);
        users.saveAndFlush(user);
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(5)).build();
        String base = "http://[::1]:5173/api/v1/auth";
        try {
            var login = client.send(HttpRequest.newBuilder(URI.create(base + "/login")).timeout(java.time.Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + user.getUsername()
                            + "\",\"password\":\"" + password + "\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode(), "Live login must succeed");
            var match = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(login.body());
            assertTrue(match.find(), "Login must return a token");
            String token = match.group(1);
            var me = client.send(HttpRequest.newBuilder(URI.create(base + "/me")).timeout(java.time.Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, me.statusCode(), "Live profile must accept the login token");
            assertTrue(me.body().contains(user.getUsername()));
            var logout = client.send(HttpRequest.newBuilder(URI.create(base + "/logout")).timeout(java.time.Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody())
                    .build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(204, logout.statusCode());
            var revoked = client.send(HttpRequest.newBuilder(URI.create(base + "/me")).timeout(java.time.Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(401, revoked.statusCode());
        } finally {
            users.deleteById(user.getUserId());
        }
    }
}
