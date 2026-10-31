package com.eggprompt.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "กรุณากรอกรหัสผ่านเดิม")
        @Size(max = 72, message = "รหัสผ่านเดิมต้องไม่เกิน 72 ตัวอักษร")
        String currentPassword,

        @NotBlank(message = "กรุณากรอกรหัสผ่านใหม่")
        @Size(
                min = 8,
                max = 72,
                message = "รหัสผ่านใหม่ต้องมีความยาว 8–72 ตัวอักษร"
        )
        String password,

        @NotBlank(message = "กรุณายืนยันรหัสผ่านใหม่")
        @Size(max = 72, message = "ยืนยันรหัสผ่านใหม่ต้องไม่เกิน 72 ตัวอักษร")
        String confirmPassword
) {
}