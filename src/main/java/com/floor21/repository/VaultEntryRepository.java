package com.floor21.repository;

import com.floor21.entity.VaultEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VaultEntryRepository extends JpaRepository<VaultEntry, UUID> {

    List<VaultEntry> findByBuilder_IdOrderByEntryDateDescCreatedAtDesc(UUID builderId);

    List<VaultEntry> findByBooking_IdOrderByEntryDateDescCreatedAtDesc(UUID bookingId);

    List<VaultEntry> findByBooking_IdOrderByEntryDateAscCreatedAtAsc(UUID bookingId);

    List<VaultEntry> findByBooking_IdAndPaymentSlabIsNotNullOrderByPaymentSlab_SortOrderAscIdAsc(
            UUID bookingId);

    List<VaultEntry> findByBooking_IdAndPaymentSlabIsNullOrderByEntryDateDescCreatedAtDesc(
            UUID bookingId);

    Optional<VaultEntry> findByBooking_IdAndPaymentSlab_Id(UUID bookingId, UUID paymentSlabId);

    Optional<VaultEntry> findByIdAndBuilder_Id(UUID id, UUID builderId);

    Optional<VaultEntry> findByIdAndBooking_IdAndBuilder_Id(UUID id, UUID bookingId, UUID builderId);

    @Query("select coalesce(sum(v.amount), 0) from VaultEntry v where v.builder.id = :builderId")
    BigDecimal sumAmountByBuilderId(@Param("builderId") UUID builderId);

    @Query("select coalesce(sum(v.amount), 0) from VaultEntry v where v.booking.id = :bookingId")
    BigDecimal sumAmountByBookingId(@Param("bookingId") UUID bookingId);

    @Query(
            "select coalesce(sum(v.amount), 0) from VaultEntry v where v.booking.id = :bookingId "
                    + "and v.paymentSlab is not null")
    BigDecimal sumAmountByBookingIdOnSlabs(@Param("bookingId") UUID bookingId);

    @Query(
            "select coalesce(sum(v.amount), 0) from VaultEntry v where v.booking.id = :bookingId "
                    + "and v.paymentSlab is null")
    BigDecimal sumAmountByBookingIdExtraOnly(@Param("bookingId") UUID bookingId);

    List<VaultEntry> findByBooking_IdAndEntryTypeOrderByEntryDateDescCreatedAtDesc(
            UUID bookingId, String entryType);

    @Query(
            "select coalesce(sum(v.amount), 0) from VaultEntry v "
                    + "where v.booking.id = :bookingId and v.entryType = :entryType")
    BigDecimal sumAmountByBookingIdAndEntryType(
            @Param("bookingId") UUID bookingId, @Param("entryType") String entryType);

    @Query(
            "select coalesce(sum(v.amount), 0) from VaultEntry v "
                    + "where v.paymentSlab.id = :slabId")
    BigDecimal sumAmountByPaymentSlabId(@Param("slabId") UUID slabId);
}
