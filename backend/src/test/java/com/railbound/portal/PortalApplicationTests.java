package com.railbound.portal;

import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InsufficientFundsException;
import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.payments.MockPaymentAdapter;
import com.railbound.portal.payments.PaymentOrder;
import com.railbound.portal.payments.PaymentOrderStatus;
import com.railbound.portal.payments.PaymentService;
import com.railbound.portal.shop.ShopService;
import com.railbound.portal.wallet.Wallet;
import com.railbound.portal.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PortalApplicationTests {

    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockPaymentAdapter mockPaymentAdapter;

    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "usr_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User(testUserId, "nakama_" + testUserId, "TestPlayer", "PLAYER");
        userRepository.save(user);
        walletService.getOrCreateWallet(testUserId);
    }

    @Test
    @DisplayName("Top-Up Gate: 1000+150 bundle credits exactly 1150 coins once")
    void testTopUpFulfillmentGate() throws Exception {
        // 1. Create checkout session for bundle_rc_1000
        PaymentOrder order = paymentService.createCheckoutSession(testUserId, "bundle_rc_1000", "mock", "idem_test_topup_1");
        assertNotNull(order);
        assertEquals(1150, order.getCoinsToCredit());
        assertEquals(999, order.getAmountMinor());

        // 2. Prepare mock webhook
        String eventId = "evt_test_" + UUID.randomUUID();
        String payload = String.format(
                "{\"type\":\"payment.succeeded\",\"eventId\":\"%s\",\"orderId\":\"%s\",\"amountMinor\":999,\"currency\":\"USD\"}",
                eventId, order.getOrderId()
        );
        String validSignature = mockPaymentAdapter.computeHmacSha256(payload, mockPaymentAdapter.getSecret());

        // 3. Process webhook
        boolean result = paymentService.processWebhook("mock", payload, validSignature);
        assertTrue(result);

        // 4. Assert wallet balance is exactly 1150
        Wallet wallet = walletService.getWalletByUserId(testUserId);
        assertEquals(1150, wallet.getRealCoins(), "Wallet should be credited with exactly 1150 Real Coins");

        // 5. Invariant check: sum(ledger) == wallet.realCoins
        assertTrue(walletService.verifyLedgerInvariant(testUserId), "Ledger sum must equal wallet balance");

        // 6. Duplicate callback test: replay identical webhook
        boolean duplicateResult = paymentService.processWebhook("mock", payload, validSignature);
        assertTrue(duplicateResult);

        // Verify balance is STILL 1150 (no duplicate credit!)
        Wallet walletAfterDuplicate = walletService.getWalletByUserId(testUserId);
        assertEquals(1150, walletAfterDuplicate.getRealCoins(), "Duplicate webhook must not credit coins again");
    }

    @Test
    @DisplayName("Security Gate: Tampered webhook signature must be rejected")
    void testTamperedSignatureRejection() {
        PaymentOrder order = paymentService.createCheckoutSession(testUserId, "bundle_rc_100", "mock", "idem_tamper_1");

        String payload = "{\"type\":\"payment.succeeded\",\"orderId\":\"" + order.getOrderId() + "\"}";
        String forgedSignature = "invalid_fake_hmac_hash_00000000000000000000000000000000";

        assertThrows(InvalidSignatureException.class, () ->
                paymentService.processWebhook("mock", payload, forgedSignature)
        );
    }

    @Test
    @DisplayName("Armory Purchase Gate: 1150 coins - 450 coin ACOG yields 700 balance and active entitlement")
    void testArmoryAcogPurchaseGate() {
        // Credit 1150 coins to player first
        walletService.creditRealCoins(testUserId, 1150, "TEST_SEED", "ref_seed_01", "Initial test credit");
        assertEquals(1150, walletService.getWalletByUserId(testUserId).getRealCoins());

        // Purchase att_scope_tactical_acog (450 coins)
        Map<String, Object> purchaseResult = shopService.purchaseItem(testUserId, "att_scope_tactical_acog", "idem_pur_01");

        assertNotNull(purchaseResult.get("purchaseId"));
        assertEquals("att_scope_tactical_acog", purchaseResult.get("itemSku"));
        assertEquals(450L, purchaseResult.get("coinsSpent"));
        assertEquals(700L, purchaseResult.get("newBalance"));

        // Verify wallet is authoritatively 700
        Wallet wallet = walletService.getWalletByUserId(testUserId);
        assertEquals(700, wallet.getRealCoins(), "Remaining Real Coins must be 700");

        // Verify ledger invariant
        assertTrue(walletService.verifyLedgerInvariant(testUserId), "Ledger invariant must hold after purchase");

        // Attempt duplicate purchase of non-stackable item
        assertThrows(DuplicateResourceException.class, () ->
                shopService.purchaseItem(testUserId, "att_scope_tactical_acog", "idem_pur_02")
        );
    }

    @Test
    @DisplayName("Armory Purchase Gate: Insufficient coins must reject without debit")
    void testInsufficientCoinsRejection() {
        // Player has 0 coins, attempts to purchase 1200 coin skin
        assertThrows(InsufficientFundsException.class, () ->
                shopService.purchaseItem(testUserId, "skin_op_shadow_operative", "idem_pur_fail")
        );

        Wallet wallet = walletService.getWalletByUserId(testUserId);
        assertEquals(0, wallet.getRealCoins(), "Balance must remain 0 after failed purchase");
    }
}
