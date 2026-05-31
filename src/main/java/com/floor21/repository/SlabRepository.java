package com.floor21.repository;

import com.floor21.entity.Slab;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SlabRepository extends JpaRepository<Slab, UUID> {

    List<Slab> findByBuilder_IdOrderBySlabNameAsc(UUID builderId);

    void deleteByBuilder_Id(UUID builderId);

    Optional<Slab> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query(
            "select s from Slab s join fetch s.builder br left join fetch s.building bl order by lower(br.companyName), "
                    + "lower(s.slabName)")
    List<Slab> findAllOrderedForAdmin();
}
