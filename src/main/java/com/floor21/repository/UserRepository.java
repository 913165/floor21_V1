package com.floor21.repository;

import com.floor21.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findFirstByEmailIgnoreCaseAndActiveTrue(String email);

    java.util.List<User> findByBuilder_IdAndActiveTrueOrderByFullNameAsc(UUID builderId);
}
