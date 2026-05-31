package com.floor21.repository;

import com.floor21.entity.UserBuildingAssignment;
import com.floor21.entity.UserBuildingAssignment.AssignmentId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserBuildingAssignmentRepository extends JpaRepository<UserBuildingAssignment, AssignmentId> {

    List<UserBuildingAssignment> findByUser_IdOrderByBuilding_BuildingNameAsc(UUID userId);

    @Query(
            """
            SELECT a.building.id FROM UserBuildingAssignment a
            WHERE a.user.id = :userId
            ORDER BY a.building.buildingName
            """)
    List<UUID> findBuildingIdsByUserId(UUID userId);

    void deleteByUser_Id(UUID userId);

    @Modifying
    @Query(
            """
            DELETE FROM UserBuildingAssignment a
            WHERE a.user.id = :userId AND a.building.builder.id = :builderId
            """)
    void deleteByUser_IdAndBuilding_Builder_Id(UUID userId, UUID builderId);

    @Query(
            """
            SELECT a.building.id FROM UserBuildingAssignment a
            WHERE a.user.id = :userId AND a.building.builder.id = :builderId
            ORDER BY a.building.buildingName
            """)
    List<UUID> findBuildingIdsByUserIdAndBuilderId(UUID userId, UUID builderId);

    @Query(
            """
            SELECT a FROM UserBuildingAssignment a
            JOIN FETCH a.building b
            WHERE a.user.id = :userId AND b.builder.id = :builderId
            ORDER BY b.buildingName
            """)
    List<UserBuildingAssignment> findByUser_IdAndBuilding_Builder_IdOrderByBuildingName(UUID userId, UUID builderId);

    boolean existsByUser_IdAndBuilding_Id(UUID userId, UUID buildingId);

    long countByUser_Id(UUID userId);

    @Query(
            """
            SELECT DISTINCT a.user.id FROM UserBuildingAssignment a
            WHERE a.building.id = :buildingId
            """)
    List<UUID> findUserIdsByBuildingId(UUID buildingId);
}
