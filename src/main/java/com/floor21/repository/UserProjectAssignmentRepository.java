package com.floor21.repository;

import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.entity.UserProjectAssignment.AssignmentId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserProjectAssignmentRepository extends JpaRepository<UserProjectAssignment, AssignmentId> {

    @Query(
            """
            SELECT a FROM UserProjectAssignment a
            JOIN FETCH a.user u
            JOIN FETCH a.builder b
            WHERE b.id = :builderId
            ORDER BY lower(u.fullName)
            """)
    List<UserProjectAssignment> findByBuilder_IdWithUser(UUID builderId);

    @Query(
            """
            SELECT a FROM UserProjectAssignment a
            JOIN FETCH a.builder b
            WHERE a.user.id = :userId
            ORDER BY lower(b.companyName)
            """)
    List<UserProjectAssignment> findByUser_IdWithBuilder(UUID userId);

    Optional<UserProjectAssignment> findFirstByUser_IdOrderByBuilder_CompanyNameAsc(UUID userId);

    Optional<UserProjectAssignment> findByUser_IdAndBuilder_Id(UUID userId, UUID builderId);

    boolean existsByUser_IdAndBuilder_Id(UUID userId, UUID builderId);

    long countByBuilder_Id(UUID builderId);

    void deleteByUser_IdAndBuilder_Id(UUID userId, UUID builderId);

    @Query(
            """
            SELECT u FROM User u
            WHERE NOT EXISTS (
                SELECT 1 FROM UserProjectAssignment a
                WHERE a.user.id = u.id AND a.builder.id = :builderId
            )
            ORDER BY lower(u.fullName)
            """)
    List<User> findUsersNotOnProject(UUID builderId);

    @Query(
            """
            SELECT u FROM User u
            WHERE NOT EXISTS (
                SELECT 1 FROM UserProjectAssignment a
                WHERE a.user.id = u.id AND a.builder.id = :builderId
            )
            AND (
                :q = ''
                OR lower(u.fullName) LIKE lower(concat('%', :q, '%'))
                OR lower(u.email) LIKE lower(concat('%', :q, '%'))
                OR lower(coalesce(u.companyName, '')) LIKE lower(concat('%', :q, '%'))
            )
            ORDER BY lower(u.fullName)
            """)
    List<User> searchUsersNotOnProject(UUID builderId, String q, Pageable pageable);

    @Query(
            """
            SELECT count(u) FROM User u
            WHERE NOT EXISTS (
                SELECT 1 FROM UserProjectAssignment a
                WHERE a.user.id = u.id AND a.builder.id = :builderId
            )
            """)
    long countUsersNotOnProject(UUID builderId);

    @Query(
            """
            SELECT a FROM UserProjectAssignment a
            JOIN FETCH a.user u
            JOIN FETCH a.builder b
            ORDER BY lower(b.companyName), lower(u.fullName)
            """)
    List<UserProjectAssignment> findAllWithUserAndBuilderOrderByLabels();
}
