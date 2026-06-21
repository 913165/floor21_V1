package com.floor21.repository;

import com.floor21.entity.VaultBookingProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VaultBookingProfileRepository extends JpaRepository<VaultBookingProfile, UUID> {

    Optional<VaultBookingProfile> findByBookingIdAndBuilder_Id(UUID bookingId, UUID builderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM VaultBookingProfile p WHERE p.builder.id = :builderId")
    void deleteByBuilder_Id(@Param("builderId") UUID builderId);
}
