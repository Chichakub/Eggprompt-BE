package com.eggprompt.backend.auth;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final Duration VALIDITY = Duration.ofHours(24);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    private final UserRepository users;
    private final PasswordResetTokenGenerator tokens;
    private final JavaMailSender mailSender;

    @Value("${app.password-reset.from}")
    private String from;

    @Value("${app.email-verification.url}")
    private String verificationUrl;

    @Transactional
    public void verify(String token) {
        String hash = tokens.hash(token);
        User user = users.findVerificationUserId(hash)
                .flatMap(users::findByIdForUpdate)
                .orElseThrow(EmailVerificationService::invalidLink);
        if (!hash.equals(user.getEmailVerificationHash())
                || !Instant.now().isBefore(user.getEmailVerificationExpiresAt())) {
            throw invalidLink();
        }

        user.setEmailVerificationHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setActive(true);
    }

    @Transactional
    public void resend(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        var userIds = users.findPendingVerificationUserIds(normalizedEmail);
        if (userIds.size() != 1) {
            return;
        }

        users.findByIdForUpdate(userIds.getFirst())
                .filter(User::isEmailVerificationPending)
                .filter(user -> normalizedEmail.equals(user.getEmail()))
                .filter(user -> !Instant.now().isBefore(
                        user.getEmailVerificationExpiresAt().minus(VALIDITY).plus(RESEND_INTERVAL)))
                .ifPresent(this::issue);
    }

    public void issue(User user) {
        String token = tokens.generate();
        user.setEmailVerificationHash(tokens.hash(token));
        user.setEmailVerificationExpiresAt(Instant.now().plus(VALIDITY));

        String link = UriComponentsBuilder.fromUriString(verificationUrl)
                .queryParam("token", token).build().encode().toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("EggPrompt — ยืนยันอีเมล");
        message.setText("เปิดลิงก์เพื่อยืนยันอีเมลบัญชี EggPrompt ภายใน 24 ชั่วโมง:\n" + link);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mailSender.send(message);
                } catch (MailException exception) {
                    log.warn("Could not send verification email for user ID {}", user.getUserId());
                }
            }
        });
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ลิงก์ยืนยันอีเมลใช้ไม่ได้หรือหมดอายุแล้ว");
    }
}
