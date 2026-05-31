package com.floor21.repository;

import com.floor21.entity.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByBuilder_IdOrderByFirstNameAscLastNameAsc(UUID builderId);

    void deleteByBuilder_Id(UUID builderId);

    Optional<Client> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query(
            "select c from Client c where c.builder.id = :builderId and "
                    + "(lower(c.firstName) like lower(concat('%', :q, '%')) or "
                    + "lower(c.lastName) like lower(concat('%', :q, '%')) or "
                    + "lower(c.mobile1) like lower(concat('%', :q, '%')) or "
                    + "lower(c.email1) like lower(concat('%', :q, '%'))) "
                    + "order by c.firstName asc")
    List<Client> search(@Param("builderId") UUID builderId, @Param("q") String q);
}
