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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    public WalletService(WalletRepository walletRepository, WalletLedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
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

    /**
     * Atomically credits real coins to a user's wallet and appends an immutable ledger entry.
     * Enforces single-credit idempotency via unique reference key.
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

        Wallet wallet = getOrCreateWallet(userId);
        long newBalance = wallet.getRealCoins() + amount;
        wallet.setRealCoins(newBalance);
        wallet.setUpdatedAt(Instant.now());
        wallet = walletRepository.save(wallet);

        String entryId = "led_" + UUID.randomUUID().toString().replace("-", "");
        WalletLedgerEntry entry = new WalletLedgerEntry(
                entryId,
                userId,
                LedgerEntryType.TOPUP,
                amount,
                newBalance,
                referenceType,
                referenceId,
                description
        );
        ledgerRepository.save(entry);

        log.info("Authoritatively credited {} Real Coins to user {}. New balance: {}", amount, userId, newBalance);
        return wallet;
    }

    /**
     * Atomically debits real coins from a user's wallet with conditional balance verification.
     */
    @Transactional
    public Wallet debitRealCoins(String userId, long amount, String referenceType, String referenceId, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }

        Wallet wallet = getOrCreateWallet(userId);
        if (wallet.getRealCoins() < amount) {
            throw new InsufficientFundsException("Insufficient Real Coins. Required: " + amount + ", Available: " + wallet.getRealCoins());
        }

        long newBalance = wallet.getRealCoins() - amount;
        wallet.setRealCoins(newBalance);
        wallet.setUpdatedAt(Instant.now());
        wallet = walletRepository.save(wallet);

        String entryId = "led_" + UUID.randomUUID().toString().replace("-", "");
        WalletLedgerEntry entry = new WalletLedgerEntry(
                entryId,
                userId,
                LedgerEntryType.PURCHASE,
                -amount,
                newBalance,
                referenceType,
                referenceId,
                description
        );
        ledgerRepository.save(entry);

        log.info("Authoritatively debited {} Real Coins from user {}. New balance: {}", amount, userId, newBalance);
        return wallet;
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
}
