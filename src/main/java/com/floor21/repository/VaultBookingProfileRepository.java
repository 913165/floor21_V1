package com.floor21.repository;

import com.floor21.entity.VaultBookingProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultBookingProfileRepository extends JpaRepository<VaultBookingProfile, UUID> {

    Optional<VaultBookingProfile> findByBookingIdAndBuilder_Id(UUID bookingId, UUID builderId);
}
