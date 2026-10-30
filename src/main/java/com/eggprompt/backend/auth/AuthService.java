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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
                user.getUserId(), user.getUsername(), user.getUserRole(), user.getFarmId());
    }
}