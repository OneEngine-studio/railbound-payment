package com.railbound.portal.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletLedgerRepository extends MongoRepository<WalletLedgerEntry, String> {
    Page<WalletLedgerEntry> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<WalletLedgerEntry> findByUserId(String userId);
    Optional<WalletLedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
    boolean existsByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
