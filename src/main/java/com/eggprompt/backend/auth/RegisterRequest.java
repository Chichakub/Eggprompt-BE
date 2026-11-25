package com.eggprompt.backend.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(

        @NotBlank(message = "กรุณากรอกชื่อผู้ใช้")
        @Pattern(regexp="[A-Za-z0-9._-]{4,24}", message="ชื่อผู้ใช้ต้องยาว 4–24 ตัว และใช้ตัวอักษรอังกฤษ ตัวเลข จุด ขีดกลาง หรือขีดล่าง")
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