package com.eggprompt.backend.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "กรุณากรอกชื่อผู้ใช้")
        @Size(max = 100, message = "ชื่อผู้ใช้ต้องไม่เกิน 100 ตัวอักษร")
        String username,

        @NotBlank(message = "กรุณากรอกรหัสผ่าน")
        @Size(min = 8, message = "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร")
        String password,

        @NotBlank(message = "กรุณากรอกอีเมล")
        @Email(message = "รูปแบบอีเมลไม่ถูกต้อง")
        @Size(max = 255, message = "อีเมลต้องไม่เกิน 255 ตัวอักษร")
        String email,

        @NotNull(message = "กรุณายอมรับเงื่อนไขการใช้งาน")
        @AssertTrue(message = "กรุณายอมรับเงื่อนไขการใช้งาน")
        Boolean accepted

) {}