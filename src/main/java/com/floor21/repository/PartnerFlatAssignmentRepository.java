package com.floor21.repository;

import com.floor21.entity.PartnerFlatAssignment;
import com.floor21.entity.PartnerFlatAssignment.AssignmentId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerFlatAssignmentRepository extends JpaRepository<PartnerFlatAssignment, AssignmentId> {

    boolean existsByBuilding_Id(UUID buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PartnerFlatAssignment a WHERE a.building.id = :buildingId")
    void deleteByBuilding_Id(@Param("buildingId") UUID buildingId);

    List<PartnerFlatAssignment> findByBuilding_Id(UUID buildingId);

    Optional<PartnerFlatAssignment> findByFlat_Id(UUID flatId);

    @Query(
            """
            select a.flat.id from PartnerFlatAssignment a
            where a.user.id = :userId and a.building.id = :buildingId
            """)
    List<UUID> findFlatIdsByUser_IdAndBuilding_Id(
            @Param("userId") UUID userId, @Param("buildingId") UUID buildingId);
}
