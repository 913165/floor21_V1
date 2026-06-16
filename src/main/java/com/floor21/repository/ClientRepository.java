package com.floor21.repository;

import com.floor21.entity.Client;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Page<Client> findByBuilder_IdOrderByFirstNameAscLastNameAsc(UUID builderId, Pageable pageable);

    @Query(
            value =
                    "select c from Client c join c.builder b where b.platformAdmin = false",
            countQuery =
                    "select count(c) from Client c join c.builder b where b.platformAdmin = false")
    Page<Client> findAllForPlatformAdmin(Pageable pageable);

    @Query(
            value =
                    "select c from Client c join c.builder b where b.platformAdmin = false and "
                            + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                            + "lower(c.email1) like lower(concat('%', :q, '%')))",
            countQuery =
                    "select count(c) from Client c join c.builder b where b.platformAdmin = false and "
                            + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                            + "lower(c.email1) like lower(concat('%', :q, '%')))")
    Page<Client> searchAllForPlatformAdmin(@Param("q") String q, Pageable pageable);

    void deleteByBuilder_Id(UUID builderId);

    Optional<Client> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query("select c from Client c join fetch c.builder where c.id = :id")
    Optional<Client> findByIdWithBuilder(@Param("id") UUID id);

    @Query(
            value =
                    "select c from Client c where c.builder.id = :builderId and "
                            + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                            + "lower(c.email1) like lower(concat('%', :q, '%')))",
            countQuery =
                    "select count(c) from Client c where c.builder.id = :builderId and "
                            + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                            + "lower(c.email1) like lower(concat('%', :q, '%')))")
    Page<Client> search(@Param("builderId") UUID builderId, @Param("q") String q, Pageable pageable);

    @Query(
            value =
                    "select c from Client c where c.builder.id = :builderId and c.id in "
                            + "(select bk.client.id from Booking bk join bk.flat f where f.building.id = :buildingId "
                            + "and (bk.status is null or bk.status <> 'CANCELLED'))",
            countQuery =
                    "select count(c) from Client c where c.builder.id = :builderId and c.id in "
                            + "(select bk.client.id from Booking bk join bk.flat f where f.building.id = :buildingId "
                            + "and (bk.status is null or bk.status <> 'CANCELLED'))")
    Page<Client> findByBuilder_IdAndActiveBookingInBuilding(
            @Param("builderId") UUID builderId,
            @Param("buildingId") UUID buildingId,
            Pageable pageable);

    @Query(
            value =
                    "select c from Client c where c.builder.id = :builderId and c.id in "
                            + "(select bk.client.id from Booking bk join bk.flat f where f.building.id = :buildingId "
                            + "and (bk.status is null or bk.status <> 'CANCELLED')) and "
                            + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                            + "lower(c.email1) like lower(concat('%', :q, '%')))",
            countQuery =
                    "select count(c) from Client c where c.builder.id = :builderId and c.id in "
                            + "(select bk.client.id from Booking bk join bk.flat f where f.building.id = :buildingId "
                            + "and (bk.status is null or bk.status <> 'CANCELLED')) and "
                            + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                            + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                            + "lower(c.email1) like lower(concat('%', :q, '%')))")
    Page<Client> searchByBuilder_IdAndActiveBookingInBuilding(
            @Param("builderId") UUID builderId,
            @Param("buildingId") UUID buildingId,
            @Param("q") String q,
            Pageable pageable);
}
