package com.eggprompt.backend.house;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HouseRepository extends JpaRepository<House, Integer> {

    List<House> findByFarmIdAndDeletedAtIsNullOrderByHouseIdAsc(String farmId);

    List<House> findByDeletedAtIsNullOrderByFarmIdAscHouseIdAsc();

    Optional<House> findByHouseIdAndDeletedAtIsNull(Integer houseId);

    boolean existsByFarmIdAndHouseNameAndDeletedAtIsNull(String farmId, String houseName);

    boolean existsByFarmIdAndHouseNameAndDeletedAtIsNullAndHouseIdNot(
            String farmId, String houseName, Integer houseId);

    // นับฝูงที่ยังเลี้ยงอยู่ในโรงเรือนนี้ (ยังไม่มี Flock entity จึงใช้ native query)
    @Query(value = """
            SELECT COUNT(*) FROM flocks
            WHERE house_id = :houseId
              AND deleted_at IS NULL
              AND status <> 'RETIRED'
            """, nativeQuery = true)
    long countActiveFlocks(@Param("houseId") Integer houseId);
}