package com.eggprompt.backend.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditProfileRequest(
        @NotBlank(message = "กรุณากรอกชื่อ - สกุล")
        @Size(max = 255, message = "ชื่อ - สกุลต้องไม่เกิน 255 ตัวอักษร")
        String fullName,

        @NotBlank(message = "กรุณากรอกอีเมล")
        @Email(message = "รูปแบบอีเมลไม่ถูกต้อง")
        @Size(max = 255, message = "อีเมลต้องไม่เกิน 255 ตัวอักษร")
        String email,

        @NotBlank(message = "กรุณากรอกเบอร์โทรศัพท์")
        @Size(max = 20, message = "เบอร์โทรศัพท์ต้องไม่เกิน 20 ตัวอักษร")
        String phoneNumber
) {
}