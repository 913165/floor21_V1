package com.floor21.repository;

import com.floor21.entity.MilestoneSampleTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneSampleTemplateRepository extends JpaRepository<MilestoneSampleTemplate, UUID> {

    List<MilestoneSampleTemplate> findAllByOrderBySortOrderAscNameAsc();

    long count();
}
