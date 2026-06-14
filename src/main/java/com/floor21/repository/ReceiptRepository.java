package com.floor21.repository;

import com.floor21.entity.Receipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    @Query(
            "select r from Receipt r left join fetch r.depositBank where r.booking.id = :bookingId and "
                    + "r.builder.id = :builderId order by r.receiptSerial asc, r.createdAt asc")
    List<Receipt> findByBooking_IdAndBuilder_IdOrderByReceiptSerialAscCreatedAtAsc(
            @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select r from Receipt r where r.booking.id = :bookingId and r.builder.id = :builderId "
                    + "and (r.dishonoured = false or r.dishonoured is null) "
                    + "order by r.receiptDate asc, r.receiptSerial asc, r.createdAt asc")
    List<Receipt> findActiveByBooking_IdOrderByReceiptDateAsc(
            @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select r from Receipt r left join fetch r.depositBank where r.id = :rid and r.booking.id = :bookingId "
                    + "and r.builder.id = :builderId")
    Optional<Receipt> findByIdAndBooking_IdAndBuilder_Id(
            @Param("rid") UUID id, @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    Optional<Receipt> findFirstByBooking_IdAndBuilder_IdOrderByReceiptSerialDesc(
            UUID bookingId, UUID builderId);

    @Query(
            "select coalesce(max(r.receiptSerial),0) from Receipt r where r.booking.id = :bookingId and "
                    + "r.builder.id = :builderId")
    int findMaxReceiptSerialByBookingId(
            @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select r from Receipt r left join fetch r.depositBank join fetch r.builder "
                    + "join fetch r.booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where r.id = :id and b.id = :bookingId and r.builder.id = :builderId")
    Optional<Receipt> findByIdForPrintView(
            @Param("id") UUID id, @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select coalesce(sum(r.amount),0) from Receipt r where r.booking.id = :bookingId and "
                    + "r.builder.id = :builderId and (r.dishonoured = false or r.dishonoured is null)")
    java.math.BigDecimal sumAmountForBooking(
            @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select coalesce(sum(coalesce(r.amountConsideration,0) + coalesce(r.amountInterestAgreement,0) + "
                    + "coalesce(r.amountExtraCharges,0) + coalesce(r.amountTds,0)),0) from Receipt r "
                    + "where r.booking.id = :bookingId and r.builder.id = :builderId "
                    + "and (r.dishonoured = false or r.dishonoured is null)")
    java.math.BigDecimal sumAgreementCredits(
            @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select coalesce(sum(coalesce(r.amountGstComponent,0) + coalesce(r.amountInterestGst,0)),0) "
                    + "from Receipt r where r.booking.id = :bookingId and r.builder.id = :builderId "
                    + "and (r.dishonoured = false or r.dishonoured is null)")
    java.math.BigDecimal sumGstCredits(@Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);

    @Query(
            "select r from Receipt r "
                    + "join fetch r.booking b "
                    + "join fetch b.client "
                    + "join fetch b.flat f "
                    + "join fetch f.building "
                    + "where r.builder.id = :builderId "
                    + "order by r.receiptDate desc, r.createdAt desc")
    List<Receipt> findForTenantList(@Param("builderId") UUID builderId);

    long countByBooking_IdAndBuilder_Id(UUID bookingId, UUID builderId);

    void deleteByBooking_IdAndBuilder_Id(UUID bookingId, UUID builderId);
}
