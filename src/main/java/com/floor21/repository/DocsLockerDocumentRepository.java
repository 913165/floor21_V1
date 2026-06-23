package com.floor21.repository;

import com.floor21.entity.DocsLockerDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocsLockerDocumentRepository extends JpaRepository<DocsLockerDocument, UUID> {

    @Query(
            "select d from DocsLockerDocument d "
                    + "left join fetch d.booking b left join fetch b.flat f left join fetch f.building "
                    + "left join fetch d.uploadedBy "
                    + "where d.builder.id = :builderId order by d.createdAt desc")
    List<DocsLockerDocument> findByBuilder_IdOrderByCreatedAtDesc(@Param("builderId") UUID builderId);

    @Query(
            "select d from DocsLockerDocument d "
                    + "left join fetch d.booking b left join fetch b.flat "
                    + "where d.id = :id and d.builder.id = :builderId")
    Optional<DocsLockerDocument> findByIdAndBuilder_Id(@Param("id") UUID id, @Param("builderId") UUID builderId);

    void deleteByBuilder_Id(UUID builderId);
}
