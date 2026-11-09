package com.eggprompt.backend.farm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "farms")
@Getter
@Setter
public class Farm {

    @Id
    @Column(name = "farm_id", length = 20)
    private String farmId;

    @Column(name = "farm_name", nullable = false, length = 255)
    private String farmName;

    @Column(name = "address", nullable = false, columnDefinition = "text")
    private String address;

    @Column(name = "livestock_reg_number", length = 100)
    private String farmRegistrationNumber;

    @Column(name = "tax_id", nullable = false, length = 20)
    private String taxId;

    @Column(name = "start_date", nullable = false)
    private LocalDate operationStartDate;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String contactPhone;
}