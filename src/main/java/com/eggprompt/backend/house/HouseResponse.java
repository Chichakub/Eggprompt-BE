package com.eggprompt.backend.house;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record HouseResponse(
        Integer houseId,
        String farmId,
        String houseName,
        BigDecimal widthM,
        BigDecimal lengthM,
        BigDecimal areaSqm,
        Integer capacity,
        String status,
        LocalDate lastBuiltOrRenovatedDate
) {
    public static HouseResponse from(House h) {
        return new HouseResponse(
                h.getHouseId(), h.getFarmId(), h.getHouseName(),
                h.getWidthM(), h.getLengthM(),
                h.getWidthM().multiply(h.getLengthM()).setScale(2, RoundingMode.HALF_UP),
                h.getCapacity(),
                h.getStatus(),
                h.getLastBuiltOrRenovatedDate());
    }
}
