package com.eggprompt.backend.house;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HouseRepository extends JpaRepository<House, Integer> {

    List<House> findByFarmIdAndDeletedAtIsNullOrderByHouseIdAsc(String farmId);

    List<House> findByDeletedAtIsNullOrderByFarmIdAscHouseIdAsc();
}