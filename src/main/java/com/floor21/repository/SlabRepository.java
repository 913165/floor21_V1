package com.floor21.repository;

import com.floor21.entity.Slab;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlabRepository extends JpaRepository<Slab, UUID> {

    List<Slab> findByBuilder_IdOrderBySlabNameAsc(UUID builderId);

    void deleteByBuilder_Id(UUID builderId);

    void deleteByBuilder_IdAndBuildingIsNull(UUID builderId);

    void deleteByBuilding_Id(UUID buildingId);

    Optional<Slab> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query(
            "select s from Slab s join fetch s.builder br left join fetch s.building bl "
                    + "order by lower(br.companyName), coalesce(s.sortOrder, 999999), lower(s.slabName), s.id")
    List<Slab> findAllOrderedForAdmin();

    @Query(
            """
            select s from Slab s
            join fetch s.builder br
            left join fetch s.building bl
            where (:builderId is null or br.id = :builderId)
              and (:buildingId is null or bl.id = :buildingId)
              and (
                    :search is null or :search = ''
                    or lower(s.slabName) like lower(concat('%', :search, '%'))
                    or (bl is not null and lower(bl.buildingName) like lower(concat('%', :search, '%')))
                    or lower(br.companyName) like lower(concat('%', :search, '%'))
                  )
            order by lower(br.companyName), lower(coalesce(bl.buildingName, 'zzzz')),
                     coalesce(s.sortOrder, 999999), lower(s.slabName), s.id
            """)
    List<Slab> findFilteredForAdmin(
            @Param("builderId") UUID builderId,
            @Param("buildingId") UUID buildingId,
            @Param("search") String search);

    @Query(
            """
            select s from Slab s
            where s.building.id = :buildingId
              and (s.active is null or s.active = true)
              and s.suggestedPercent is not null
            order by coalesce(s.sortOrder, 999999), s.id
            """)
    List<Slab> findActiveMilestonesByBuilding_Id(@Param("buildingId") UUID buildingId);
}
