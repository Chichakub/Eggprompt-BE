package com.eggprompt.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้อง")
        @Size(min = 43, max = 43, message = "ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้อง")
        String token,

        @NotBlank(message = "กรุณากรอกรหัสผ่านใหม่")
        @Size(min = 8, max = 72,
                message = "รหัสผ่านต้องมีความยาว 8–72 ตัวอักษร")
        String password,

        @NotBlank(message = "กรุณายืนยันรหัสผ่านใหม่")
        String confirmPassword
) {
}