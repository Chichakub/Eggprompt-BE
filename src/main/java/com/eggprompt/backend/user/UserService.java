package com.eggprompt.backend.user;

import com.eggprompt.backend.auth.UserProfileResponse;
import com.eggprompt.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserProfileResponse updateMyProfile(String currentUsername, EditProfileRequest request) {
        User account = userRepository.findByUsernameAndDeletedAtIsNull(currentUsername)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ไม่พบบัญชีผู้ใช้"));

        User user = userRepository.findByIdForUpdate(account.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ไม่พบบัญชีผู้ใช้"));

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "บัญชีนี้ถูกปิดใช้งาน");
        }

        String username = request.username().trim();
        String fullName = request.fullName().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = normalizePhone(request.phoneNumber());
        String position = request.position() == null || request.position().isBlank()
                ? null
                : request.position().trim();

        if (username.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณากรอกชื่อผู้ใช้");
        if (fullName.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "กรุณากรอกชื่อ - สกุล");

        Integer id = user.getUserId();
        if (userRepository.existsByUsernameAndUserIdNot(username, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว");
        }
        if (userRepository.existsByEmailNormalizedAndUserIdNot(email, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "อีเมลนี้ถูกใช้งานแล้ว");
        }

        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPosition(position);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ชื่อผู้ใช้หรืออีเมลนี้ถูกใช้งานแล้ว");
        }

        return new UserProfileResponse(
                user.getUserId(), user.getUsername(), user.getUserRole(), user.getFarmId(),
                user.getUserCode(), user.getFullName(), user.getEmail(),
                user.getPhoneNumber(), user.getPosition(),
                user.isActive() ? "active" : "inactive");
    }

    static String normalizePhone(String raw) {
        String digits = raw.replaceAll("[\\s-]", "");
        if (!digits.matches("0\\d{8,9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "รูปแบบเบอร์โทรศัพท์ไม่ถูกต้อง");
        }
        return digits.length() == 10
                ? digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6)
                : digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
    }
}