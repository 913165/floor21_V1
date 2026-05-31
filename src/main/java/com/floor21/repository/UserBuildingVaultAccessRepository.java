package com.floor21.repository;

import com.floor21.entity.UserBuildingVaultAccess;
import com.floor21.entity.UserBuildingVaultAccess.GrantId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBuildingVaultAccessRepository
        extends JpaRepository<UserBuildingVaultAccess, GrantId> {

    @Query(
            """
            select g from UserBuildingVaultAccess g
            join fetch g.user u
            join fetch g.building b
            join fetch b.builder br
            order by lower(br.companyName), lower(u.fullName), lower(b.buildingName)
            """)
    List<UserBuildingVaultAccess> findAllForAdminOrderByLabels();

    boolean existsByUser_IdAndEnabledTrue(UUID userId);

    boolean existsByBuilding_Builder_IdAndEnabledTrue(UUID builderId);

    boolean existsByBuilding_IdAndEnabledTrue(UUID buildingId);

    boolean existsByUser_IdAndBuilding_IdAndEnabledTrue(UUID userId, UUID buildingId);

    void deleteByUser_IdAndBuilding_Id(UUID userId, UUID buildingId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
            """
            DELETE FROM UserBuildingVaultAccess g
            WHERE g.user.id = :userId AND g.building.builder.id = :builderId
            """)
    void deleteByUser_IdAndBuilding_Builder_Id(
            @Param("userId") UUID userId, @Param("builderId") UUID builderId);
}
