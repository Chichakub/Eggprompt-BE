package com.eggprompt.backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "users")
@Getter
@Setter
public class User {

    @Column(name = "onboarding_required", nullable = false, columnDefinition = "boolean default false")
    private boolean onboardingRequired;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "farm_id")
    private String farmId;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email_verification_hash", length = 64, unique = true)
    private String emailVerificationHash;

    @Column(name = "email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    public boolean isEmailVerificationPending() {
        return emailVerificationHash != null;
    }

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "user_code")
    private String userCode;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "position")
    private String position;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

    @Column(name = "user_role", nullable = false)
    private String userRole;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Instant lastLoginAt;

    @Column(name="auth_version",nullable=false,columnDefinition="bigint default 0")
    private long authVersion;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}