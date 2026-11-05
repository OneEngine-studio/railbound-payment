package com.railbound.portal.wallet;

import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InsufficientFundsException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.ledger.LedgerEntryType;
import com.railbound.portal.ledger.WalletLedgerEntry;
import com.railbound.portal.ledger.WalletLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The only writer of wallet balances.
 *
 * <p>CONCURRENCY: balance changes use a single atomic MongoDB findAndModify ($inc guarded by
 * {@code realCoins >= amount} for debits) instead of read-modify-write. Two simultaneous purchases
 * can therefore never overspend, and there are no optimistic-lock failures to retry. Every change
 * also appends an immutable ledger entry inside the same transaction, so
 * sum(ledger deltas) == wallet balance always holds.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;
    private final MongoTemplate mongoTemplate;

    public WalletService(WalletRepository walletRepository,
                         WalletLedgerRepository ledgerRepository,
                         MongoTemplate mongoTemplate) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Wallet getOrCreateWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, 0)));
    }

    public Wallet getWalletByUserId(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + userId));
    }

    public Page<WalletLedgerEntry> getLedgerEntries(String userId, Pageable pageable) {
        return ledgerRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /** True when a ledger entry already exists for this business reference (single-credit guard). */
    public boolean hasLedgerReference(String referenceType, String referenceId) {
        return ledgerRepository.existsByReferenceTypeAndReferenceId(referenceType, referenceId);
    }

    /**
     * Atomically credits real coins to a user's wallet and appends an immutable ledger entry.
     * Enforces single-credit idempotency via the unique (referenceType, referenceId) key.
     */
    @Transactional
    public Wallet creditRealCoins(String userId, long amount, String referenceType, String referenceId, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be strictly positive");
        }

        if (ledgerRepository.existsByReferenceTypeAndReferenceId(referenceType, referenceId)) {
            log.warn("Duplicate credit attempt detected for reference: {} / {}", referenceType, referenceId);
            throw new DuplicateResourceException("Ledger entry already exists for reference: " + referenceId);
        }

        Wallet wallet = adjustBalance(userId, amount);
        if (wallet == null) {
            throw new IllegalStateException("Could not credit wallet for user " + userId);
        }

        appendLedger(userId, LedgerEntryType.TOPUP, amount, wallet.getRealCoins(), referenceType, referenceId, description);
        log.info("Authoritatively credited {} Real Coins to user {}. New balance: {}", amount, userId, wallet.getRealCoins());
        return wallet;
    }

    /**
     * Atomically debits real coins for a purchase. Throws InsufficientFundsException (HTTP 400)
     * without changing anything when the balance is too low.
     */
    @Transactional
    public Wallet debitRealCoins(String userId, long amount, String referenceType, String referenceId, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }

        Wallet wallet = adjustBalance(userId, -amount);
        if (wallet == null) {
            long available = walletRepository.findByUserId(userId).map(Wallet::getRealCoins).orElse(0L);
            throw new InsufficientFundsException("Insufficient Real Coins. Required: " + amount + ", Available: " + available);
        }

        appendLedger(userId, LedgerEntryType.PURCHASE, -amount, wallet.getRealCoins(), referenceType, referenceId, description);
        log.info("Authoritatively debited {} Real Coins from user {}. New balance: {}", amount, userId, wallet.getRealCoins());
        return wallet;
    }

    /**
     * Non-throwing debit used by refund / chargeback reversal. Returns false (and changes nothing)
     * when the player no longer holds enough coins - a balance is never pushed below zero.
     * It deliberately does not throw so a "not enough coins" outcome cannot mark the caller's
     * surrounding transaction rollback-only.
     */
    @Transactional
    public boolean tryDebitRealCoins(String userId, long amount, LedgerEntryType type,
                                     String referenceType, String referenceId, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }
        if (ledgerRepository.existsByReferenceTypeAndReferenceId(referenceType, referenceId)) {
            log.warn("Reversal already applied for reference {} / {}", referenceType, referenceId);
            return false;
        }

        Wallet wallet = adjustBalance(userId, -amount);
        if (wallet == null) {
            return false;
        }

        appendLedger(userId, type, -amount, wallet.getRealCoins(), referenceType, referenceId, description);
        log.info("Reversed {} Real Coins ({}) for user {}. New balance: {}", amount, type, userId, wallet.getRealCoins());
        return true;
    }

    /**
     * Verifies Invariant 1: Sum of ledger deltas == current wallet realCoins.
     */
    public boolean verifyLedgerInvariant(String userId) {
        Wallet wallet = getOrCreateWallet(userId);
        List<WalletLedgerEntry> entries = ledgerRepository.findByUserId(userId);
        long sum = entries.stream().mapToLong(WalletLedgerEntry::getDelta).sum();
        boolean matches = sum == wallet.getRealCoins();
        if (!matches) {
            log.error("LEDGER INVARIANT VIOLATION for user {}: Ledger Sum={}, Wallet Balance={}",
                    userId, sum, wallet.getRealCoins());
        }
        return matches;
    }

    /**
     * Single atomic balance change. For debits the query only matches while the balance is
     * sufficient, so a null result means "insufficient funds (or no wallet)". Credits upsert so a
     * first-ever credit creates the wallet.
     */
    private Wallet adjustBalance(String userId, long delta) {
        Query query = new Query(Criteria.where("userId").is(userId));
        if (delta < 0) {
            query.addCriteria(Criteria.where("realCoins").gte(-delta));
        }
        Update update = new Update()
                .inc("realCoins", delta)
                .inc("version", 1L)
                .set("updatedAt", Instant.now());
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(delta > 0);
        return mongoTemplate.findAndModify(query, update, options, Wallet.class);
    }

    private void appendLedger(String userId, LedgerEntryType type, long delta, long balanceAfter,
                              String referenceType, String referenceId, String description) {
        String entryId = "led_" + UUID.randomUUID().toString().replace("-", "");
        ledgerRepository.save(new WalletLedgerEntry(
                entryId, userId, type, delta, balanceAfter, referenceType, referenceId, description));
    }
}
