package com.floor21.repository;

import com.floor21.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findFirstByEmailIgnoreCaseAndActiveTrue(String email);

    java.util.List<User> findByBuilder_IdAndActiveTrueOrderByFullNameAsc(UUID builderId);

    java.util.List<User> findByBuilder_IdOrderByFullNameAsc(UUID builderId);

    boolean existsByBuilder_IdAndEmailIgnoreCaseAndIdNot(UUID builderId, String email, UUID id);

    boolean existsByBuilder_IdAndEmailIgnoreCase(UUID builderId, String email);
}
