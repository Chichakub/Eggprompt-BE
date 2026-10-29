package com.eggprompt.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    // ตรวจชื่อผู้ใช้ซ้ำ รวมบัญชีที่ถูกลบแบบ Soft Delete
    boolean existsByUsername(String username);

    // ตรวจอีเมลซ้ำ โดยไม่สนตัวพิมพ์ใหญ่เล็กและช่องว่างหัวท้าย
    @Query("""
        SELECT COUNT(u) > 0
        FROM User u
        WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))
        """)
    boolean existsByEmailNormalized(@Param("email") String email);
}