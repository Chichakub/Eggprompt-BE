package com.eggprompt.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetMailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetUrl;

    public PasswordResetMailService(
            JavaMailSender mailSender,
            @Value("${app.password-reset.from}") String from,
            @Value("${app.password-reset.url}") String resetUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetUrl = resetUrl;
    }

    public void sendResetLink(
            String recipient,
            String token,
            long expirationMinutes
    ) {
        String link = UriComponentsBuilder
                .fromUriString(resetUrl)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("EggPrompt — ตั้งรหัสผ่านใหม่");
        message.setText("""
                คุณได้รับอีเมลนี้เนื่องจากมีคำขอรีเซ็ตรหัสผ่านบัญชี EggPrompt

                เปิดลิงก์ด้านล่างเพื่อตั้งรหัสผ่านใหม่:
                %s

                ลิงก์นี้มีอายุ %d นาที และใช้ได้ครั้งเดียว

                หากคุณไม่ได้เป็นผู้ส่งคำขอ สามารถละเว้นอีเมลนี้ได้
                """.formatted(link, expirationMinutes));

        mailSender.send(message);
    }
}