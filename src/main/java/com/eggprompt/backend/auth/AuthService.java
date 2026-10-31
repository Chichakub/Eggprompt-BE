package com.eggprompt.backend.auth;

import com.eggprompt.backend.exception.ApiException;
import com.eggprompt.backend.user.User;
import com.eggprompt.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator passwordResetTokenGenerator;
    private final PasswordResetMailService passwordResetMailService;

    @Value("${app.password-reset.expiration-minutes:15}")
    private long passwordResetExpirationMinutes;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (username.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "กรุณากรอกชื่อผู้ใช้"
            );
        }

        if (!Boolean.TRUE.equals(request.accepted())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "กรุณายอมรับเงื่อนไขการใช้งาน"
            );
        }

        // BCrypt รองรับรหัสผ่านได้ไม่เกิน 72 bytes
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านยาวเกินขีดจำกัด 72 bytes"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว"
            );
        }

        if (userRepository.existsByEmailNormalized(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "อีเมลนี้ถูกใช้งานแล้ว"
            );
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);

        // เก็บเฉพาะค่าแฮช ไม่เก็บรหัสผ่านจริง
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );

        // Backend กำหนด Role เอง
        user.setUserRole("Farm Admin");
        user.setActive(true);

        // ยังไม่ได้ตั้งค่าฟาร์ม
        user.setFarmId(null);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // รองรับกรณีมีคำขอสมัครข้อมูลเดียวกันเข้ามาพร้อมกัน
            if (isUniqueViolation(ex)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "ชื่อผู้ใช้หรืออีเมลนี้ถูกใช้งานแล้ว"
                );
            }

            // ข้อผิดพลาดฐานข้อมูลประเภทอื่นไม่ใช่ข้อมูลซ้ำ
            throw ex;
        }

        return new RegisterResponse(
                "ลงทะเบียนสำเร็จ",
                new RegisterResponse.UserInfo(
                        user.getUserId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getUserRole()
                )
        );
    }

    private boolean isUniqueViolation(Throwable error) {
        Throwable cause = error;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(request.username())
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                new LoginResponse.UserInfo(
                        user.getUserId(), user.getUsername(), user.getUserRole(), user.getFarmId())
        );
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    public UserProfileResponse getProfile(String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserProfileResponse(
                user.getUserId(), user.getUsername(), user.getUserRole(), user.getFarmId(),
                user.getUserCode(), user.getFullName(), user.getEmail(),
                user.getPhoneNumber(), user.getPosition(),
                user.isActive() ? "active" : "inactive");
    }
    @Transactional
    public ForgotPasswordResponse forgotPassword(
            ForgotPasswordRequest request
    ) {
        String username = request.username().trim();

        User user = userRepository
                .findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ไม่พบชื่อผู้ใช้นี้ในระบบ กรุณาตรวจสอบชื่อผู้ใช้อีกครั้ง"
                ));

        if (!user.isActive()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "บัญชีนี้ถูกปิดใช้งาน กรุณาติดต่อผู้ดูแลระบบ"
            );
        }

        if (!"Farm Admin".equals(user.getUserRole())
                || user.getEmail() == null
                || user.getEmail().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "กรุณาติดต่อผู้ดูแลระบบเพื่อรีเซ็ตรหัสผ่าน"
            );
        }

        String token = passwordResetTokenGenerator.generate();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(passwordResetTokenGenerator.hash(token));
        resetToken.setExpiresAt(
                Instant.now().plus(
                        passwordResetExpirationMinutes,
                        ChronoUnit.MINUTES
                )
        );

        passwordResetTokenRepository.saveAndFlush(resetToken);

        passwordResetMailService.sendResetLink(
                user.getEmail(),
                token,
                passwordResetExpirationMinutes
        );

        return new ForgotPasswordResponse(
                "ส่งลิงก์รีเซ็ตรหัสผ่านไปยังอีเมลที่ผูกกับบัญชีแล้ว"
        );
    }

    @Transactional
    public ResetPasswordResponse resetPassword(
            ResetPasswordRequest request
    ) {
        String password = request.password();

        boolean hasSpecialCharacter = password.chars().anyMatch(c ->
                (c >= 33 && c <= 47)
                        || (c >= 58 && c <= 64)
                        || (c >= 91 && c <= 96)
                        || (c >= 123 && c <= 126)
        );

        if (password.length() < 8
                || !password.matches("(?s).*[A-Z].*")
                || !password.matches("(?s).*[a-z].*")
                || !password.matches("(?s).*[0-9].*")
                || !hasSpecialCharacter) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านยังไม่ผ่านเงื่อนไขทั้งหมด"
            );
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านยาวเกินขีดจำกัด 72 bytes"
            );
        }

        if (!password.equals(request.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านทั้งสองช่องไม่ตรงกัน"
            );
        }

        String tokenHash = passwordResetTokenGenerator.hash(request.token());

        Integer userId = passwordResetTokenRepository
                .findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ"
                ));

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ"
                ));

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ"
                ));

        Instant now = Instant.now();

        if (resetToken.getUsedAt() != null
                || !resetToken.getExpiresAt().isAfter(now)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุ"
            );
        }

        if (!user.isActive()
                || !"Farm Admin".equals(user.getUserRole())
                || user.getEmail() == null
                || user.getEmail().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "บัญชีนี้ไม่สามารถรีเซ็ตรหัสผ่านผ่านลิงก์ได้ กรุณาติดต่อผู้ดูแลระบบ"
            );
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.saveAndFlush(user);

        passwordResetTokenRepository.invalidateUnusedTokensForUser(
                userId,
                now
        );

        return new ResetPasswordResponse("ตั้งรหัสผ่านใหม่สำเร็จ");
    }

    @Transactional
    public ResetPasswordResponse changePassword(
            String username,
            ChangePasswordRequest request
    ) {
        User account = userRepository
                .findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ไม่พบบัญชีผู้ใช้"
                ));

        // ล็อกข้อมูลบัญชีระหว่างเปลี่ยนรหัสผ่าน
        User user = userRepository
                .findByIdForUpdate(account.getUserId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ไม่พบบัญชีผู้ใช้"
                ));

        if (!user.isActive()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "บัญชีนี้ถูกปิดใช้งาน"
            );
        }

        String currentPassword = request.currentPassword();
        String password = request.password();

        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(
                currentPassword,
                user.getPasswordHash()
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านเดิมไม่ถูกต้อง"
            );
        }

        boolean hasSpecialCharacter = password.chars().anyMatch(c ->
                (c >= 33 && c <= 47)
                        || (c >= 58 && c <= 64)
                        || (c >= 91 && c <= 96)
                        || (c >= 123 && c <= 126)
        );

        if (password.length() < 8
                || !password.matches("(?s).*[A-Z].*")
                || !password.matches("(?s).*[a-z].*")
                || !password.matches("(?s).*[0-9].*")
                || !hasSpecialCharacter) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านยังไม่ผ่านเงื่อนไขทั้งหมด"
            );
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านใหม่ต้องมีความยาวไม่เกิน 72 bytes"
            );
        }

        if (!password.equals(request.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านทั้งสองช่องไม่ตรงกัน"
            );
        }

        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "รหัสผ่านใหม่ต้องต่างจากรหัสผ่านเดิม"
            );
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.saveAndFlush(user);

        // ยกเลิกลิงก์รีเซ็ตรหัสผ่านที่ยังไม่ได้ใช้
        passwordResetTokenRepository.invalidateUnusedTokensForUser(
                user.getUserId(),
                Instant.now()
        );

        return new ResetPasswordResponse("เปลี่ยนรหัสผ่านสำเร็จ");
    }
}