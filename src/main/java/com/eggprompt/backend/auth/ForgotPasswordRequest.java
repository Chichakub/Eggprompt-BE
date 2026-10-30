package com.eggprompt.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "กรุณากรอกชื่อผู้ใช้")
        @Size(max = 100, message = "ชื่อผู้ใช้ต้องไม่เกิน 100 ตัวอักษร")
        String username
) {
}