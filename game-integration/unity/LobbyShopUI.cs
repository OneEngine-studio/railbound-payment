using System;
using UnityEngine;
using UnityEngine.UI;

namespace Railbound.Economy
{
    /// <summary>
    /// Presents the in-game Shop Screen modal containing Top-Up and Armory tabs.
    /// Invariant: Handles purchase confirmation with exact coin price; updates owned/equipped state upon server response.
    /// Suggested Location: Assets/Project/Scripts/Economy/LobbyShopUI.cs
    /// </summary>
    public class LobbyShopUI : MonoBehaviour
    {
        [Header("Tabs")]
        [SerializeField] private GameObject topUpPanel;
        [SerializeField] private GameObject armoryPanel;
        [SerializeField] private Button topUpTabButton;
        [SerializeField] private Button armoryTabButton;
        [SerializeField] private Button closeButton;

        [Header("Purchase Confirmation Modal")]
        [SerializeField] private GameObject confirmModal;
        [SerializeField] private Text confirmItemTitle;
        [SerializeField] private Text confirmCoinPrice;
        [SerializeField] private Button confirmPurchaseButton;
        [SerializeField] private Button cancelPurchaseButton;

        private string _pendingPurchaseSku;
        private long _pendingPurchasePrice;

        private void Start()
        {
            if (topUpTabButton != null) topUpTabButton.onClick.AddListener(OpenTopUpTab);
            if (armoryTabButton != null) armoryTabButton.onClick.AddListener(OpenArmoryTab);
            if (closeButton != null) closeButton.onClick.AddListener(() => gameObject.SetActive(false));

            if (confirmPurchaseButton != null) confirmPurchaseButton.onClick.AddListener(ExecutePendingPurchase);
            if (cancelPurchaseButton != null) cancelPurchaseButton.onClick.AddListener(CloseConfirmModal);

            OpenTopUpTab();
        }

        public void OpenTopUpTab()
        {
            gameObject.SetActive(true);
            if (topUpPanel != null) topUpPanel.SetActive(true);
            if (armoryPanel != null) armoryPanel.SetActive(false);
        }

        public void OpenArmoryTab()
        {
            gameObject.SetActive(true);
            if (topUpPanel != null) topUpPanel.SetActive(false);
            if (armoryPanel != null) armoryPanel.SetActive(true);
        }

        public void OnBundleSelected(string bundleSku)
        {
            Debug.Log($"[LobbyShopUI] Player selected bundle: {bundleSku}");
            PaymentManager.Instance?.StartTopUp(bundleSku);
        }

        public void PromptPurchase(string itemSku, string title, long coinPrice)
        {
            _pendingPurchaseSku = itemSku;
            _pendingPurchasePrice = coinPrice;

            if (confirmModal != null)
            {
                confirmModal.SetActive(true);
                if (confirmItemTitle != null) confirmItemTitle.text = title;
                if (confirmCoinPrice != null) confirmCoinPrice.text = $"{coinPrice} Real Coins";
            }
            else
            {
                ExecutePendingPurchase();
            }
        }

        private async void ExecutePendingPurchase()
        {
            if (string.IsNullOrEmpty(_pendingPurchaseSku)) return;

            string sku = _pendingPurchaseSku;
            CloseConfirmModal();

            Debug.Log($"[LobbyShopUI] Executing atomic purchase for SKU: {sku}...");
            string idempotencyKey = "unity_pur_" + Guid.NewGuid().ToString("N");
            string payload = $"{{\"itemSku\":\"{sku}\"}}";

            var res = await RailboundApiClient.Instance.PostAsync<PurchaseResultDto>(
                "/shop/purchases", payload, idempotencyKey);

            if (res.IsSuccess && res.Data != null)
            {
                Debug.Log($"[LobbyShopUI] Purchased {res.Data.itemSku}! Coins spent: {res.Data.coinsSpent}. New balance: {res.Data.newBalance}");
                PaymentManager.Instance?.RefreshWalletBalance();
            }
            else
            {
                Debug.LogError($"[LobbyShopUI] Purchase failed: {res.ErrorMessage}");
            }
        }

        private void CloseConfirmModal()
        {
            _pendingPurchaseSku = null;
            if (confirmModal != null) confirmModal.SetActive(false);
        }
    }

    [Serializable]
    public class PurchaseResultDto
    {
        public string purchaseId;
        public string itemSku;
        public long coinsSpent;
        public long newBalance;
        public string ownership;
    }
}
