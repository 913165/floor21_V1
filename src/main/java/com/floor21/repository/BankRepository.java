package com.floor21.repository;

import com.floor21.entity.Bank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, UUID> {

    List<Bank> findByBuilder_IdOrderByBankNameAscBranchAscIdAsc(UUID builderId);

    Optional<Bank> findByIdAndBuilder_Id(UUID id, UUID builderId);
}
