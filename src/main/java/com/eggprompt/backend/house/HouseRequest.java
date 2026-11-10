package com.eggprompt.backend.house;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HouseRequest(
        // ใช้เฉพาะ Super Admin ตอนสร้าง (Farm Admin ระบบใช้ farmId ของตนเอง)
        @Size(max = 20, message = "farmId ยาวเกินไป") String farmId,

        @NotBlank(message = "กรุณากรอกชื่อโรงเรือน")
        @Size(max = 100, message = "ชื่อโรงเรือนต้องไม่เกิน 100 ตัวอักษร") String houseName,

        @NotBlank(message = "กรุณาระบุประเภทโรงเรือน")
        @Size(max = 100, message = "ประเภทโรงเรือนต้องไม่เกิน 100 ตัวอักษร") String houseType,

        @NotNull(message = "กรุณาระบุวันที่เปิดใช้งาน") LocalDate openingDate,

        @NotNull(message = "กรุณาระบุความกว้าง")
        @DecimalMin(value = "0.0", inclusive = false, message = "ความกว้างต้องมากกว่า 0")
        @Digits(integer = 6, fraction = 2, message = "ความกว้างรูปแบบไม่ถูกต้อง") BigDecimal widthM,

        @NotNull(message = "กรุณาระบุความยาว")
        @DecimalMin(value = "0.0", inclusive = false, message = "ความยาวต้องมากกว่า 0")
        @Digits(integer = 6, fraction = 2, message = "ความยาวรูปแบบไม่ถูกต้อง") BigDecimal lengthM,

        @NotNull(message = "กรุณาระบุความจุ")
        @Min(value = 1, message = "ความจุต้องมากกว่า 0") Integer capacity,

        // ไม่ส่งมา = ACTIVE (ตอนสร้าง) / คงค่าเดิม (ตอนแก้ไข)
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "สถานะต้องเป็น ACTIVE หรือ INACTIVE") String status,

        @Min(value = 0, message = "จำนวนพัดลมต้องไม่ติดลบ") Integer ventilationFanCount,
        @Size(max = 100, message = "ระบบน้ำต้องไม่เกิน 100 ตัวอักษร") String waterSystem,
        @Size(max = 100, message = "ระบบให้อาหารต้องไม่เกิน 100 ตัวอักษร") String feedingSystem,
        @Size(max = 100, message = "การเก็บไข่ต้องไม่เกิน 100 ตัวอักษร") String eggCollectionSystem,
        @Size(max = 100, message = "โปรแกรมแสงต้องไม่เกิน 100 ตัวอักษร") String lightingProgram,
        LocalDate lastBuiltOrRenovatedDate
) {}