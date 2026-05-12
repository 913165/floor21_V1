package com.floor21.repository;

import com.floor21.entity.Builder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuilderRepository extends JpaRepository<Builder, UUID> {

    Optional<Builder> findByEmailIgnoreCase(String email);

    List<Builder> findAllByOrderByCompanyNameAsc();
}
