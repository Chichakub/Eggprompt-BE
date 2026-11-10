package com.eggprompt.backend.house;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "houses")
@Getter
@Setter
public class House {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "house_id")
    private Integer houseId;

    @Column(name = "farm_id", nullable = false, length = 20)
    private String farmId;

    @Column(name = "house_name", nullable = false, length = 100)
    private String houseName;

    @Column(name = "house_type", nullable = false, length = 100)
    private String houseType;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(name = "width_m", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthM;

    @Column(name = "length_m", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthM;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "ventilation_fan_count")
    private Integer ventilationFanCount;

    @Column(name = "water_system", length = 100)
    private String waterSystem;

    @Column(name = "feeding_system", length = 100)
    private String feedingSystem;

    @Column(name = "egg_collection_system", length = 100)
    private String eggCollectionSystem;

    @Column(name = "lighting_program", length = 100)
    private String lightingProgram;

    @Column(name = "last_built_or_renovated_date")
    private LocalDate lastBuiltOrRenovatedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}