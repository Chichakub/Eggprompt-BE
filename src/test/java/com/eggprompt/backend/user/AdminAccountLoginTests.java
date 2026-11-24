package com.eggprompt.backend.user;

import com.eggprompt.backend.auth.*;
import com.eggprompt.backend.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccountLoginTests {
    @Test void firstLoginPasswordChangeFinishesTemporaryCredentialFlow() {
        var repo=mock(UserRepository.class);var encoder=new BCryptPasswordEncoder();var u=new User();
        u.setUserId(3);u.setUsername("staff");u.setActive(true);u.setMustChangePassword(true);u.setPasswordHash(encoder.encode("Temp123!"));
        when(repo.findByUsernameAndDeletedAtIsNull("staff")).thenReturn(Optional.of(u));when(repo.findByIdForUpdate(3)).thenReturn(Optional.of(u));
        var auth=new AuthService(repo,encoder,new JwtService("",3600),mock(PasswordResetTokenRepository.class),
            new PasswordResetTokenGenerator(),mock(PasswordResetMailService.class),mock(EmailVerificationService.class));
        auth.changePassword("staff",new ChangePasswordRequest("Temp123!","MyOwnPass123!","MyOwnPass123!"));
        assertFalse(u.getMustChangePassword());assertEquals(1,u.getAuthVersion());
        assertTrue(encoder.matches("MyOwnPass123!",u.getPasswordHash()));assertFalse(encoder.matches("Temp123!",u.getPasswordHash()));
    }
    @Test void noEmailMayLoginButPendingEmailCannotLogin() {
        var repo=mock(UserRepository.class);var encoder=new BCryptPasswordEncoder();
        var u=new User();u.setUserId(3);u.setUsername("staff");u.setUserRole("Farm Staff");u.setFarmId("F1");
        u.setActive(true);u.setMustChangePassword(true);u.setPasswordHash(encoder.encode("Temp123!"));
        when(repo.findByUsernameForUpdate("staff")).thenReturn(Optional.of(u));
        var auth=new AuthService(repo,encoder,new JwtService("",3600),mock(PasswordResetTokenRepository.class),
                new PasswordResetTokenGenerator(),mock(PasswordResetMailService.class),mock(EmailVerificationService.class));
        assertTrue(auth.login(new LoginRequest("staff","Temp123!")).user().mustChangePassword());
        u.setEmail("staff@example.com");u.setEmailVerificationHash("pending");
        // Even an accidental active=true cannot bypass pending email verification.
        assertEquals("EMAIL_VERIFICATION_REQUIRED",assertThrows(ApiException.class,()->auth.login(new LoginRequest("staff","Temp123!"))).getMessage());
        u.setEmailVerificationHash(null);
        assertNotNull(auth.login(new LoginRequest("staff","Temp123!" )).accessToken());
    }
}
