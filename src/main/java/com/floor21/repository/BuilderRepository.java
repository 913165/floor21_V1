package com.floor21.repository;

import com.floor21.entity.Builder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BuilderRepository extends JpaRepository<Builder, UUID> {

    Optional<Builder> findByEmailIgnoreCase(String email);

    Optional<Builder> findFirstByPlatformAdminTrue();

    List<Builder> findAllByOrderByCompanyNameAsc();

    @Query("select b from Builder b where b.platformAdmin = false order by lower(b.companyName)")
    List<Builder> findAllTenantsOrderByCompanyNameAsc();

    @Query("select b from Builder b where b.platformAdmin = false order by b.createdAt desc, lower(b.companyName)")
    List<Builder> findAllTenantsOrderByCreatedAtDesc();

    @Query("select b from Builder b where b.platformAdmin = true and b.email is not null order by lower(b.email)")
    List<Builder> findAllPlatformAdminsOrderByEmailAsc();

    long countByPlatformAdminFalse();

    long countByPlatformAdminFalseAndActiveTrue();

    long countByPlatformAdminFalseAndActiveFalse();
}
