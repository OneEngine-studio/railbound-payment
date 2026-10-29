using System;
using System.Collections;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;

namespace Railbound.Economy
{
    /// <summary>
    /// Manages player top-up initiation, browser launching, and order polling.
    /// Invariant: Never grants coins locally; awaits authoritative server fulfillment.
    /// Suggested Location: Assets/Project/Scripts/Economy/PaymentManager.cs
    /// </summary>
    public class PaymentManager : MonoBehaviour
    {
        public static PaymentManager Instance { get; private set; }

        public event Action<long> OnWalletUpdated;
        public event Action<string, string> OnPaymentStarted; // orderId, checkoutUrl
        public event Action<string> OnPaymentCompleted;       // orderId
        public event Action<string, string> OnPaymentFailed;  // orderId, reason

        private string _activeOrderId;
        private CancellationTokenSource _pollingCts;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        /// <summary>
        /// Request a hosted checkout session for the chosen coin bundle.
        /// </summary>
        public async void StartTopUp(string bundleSku)
        {
            if (!RailboundApiClient.Instance.IsAuthenticated)
            {
                Debug.LogError("[PaymentManager] Cannot initiate checkout: Player not authenticated.");
                return;
            }

            string idempotencyKey = "unity_" + Guid.NewGuid().ToString("N");
            string payload = $"{{\"bundleSku\":\"{bundleSku}\",\"provider\":\"mock\"}}";

            Debug.Log($"[PaymentManager] Initiating checkout for {bundleSku}...");
            var response = await RailboundApiClient.Instance.PostAsync<CheckoutSessionDto>(
                "/payments/checkout-sessions", payload, idempotencyKey);

            if (!response.IsSuccess || response.Data == null)
            {
                Debug.LogError($"[PaymentManager] Checkout session failed: {response.ErrorMessage}");
                OnPaymentFailed?.Invoke(null, response.ErrorMessage);
                return;
            }

            _activeOrderId = response.Data.orderId;
            string checkoutUrl = response.Data.checkoutUrl;
            Debug.Log($"[PaymentManager] Order created: {_activeOrderId}. Opening hosted checkout: {checkoutUrl}");

            OnPaymentStarted?.Invoke(_activeOrderId, checkoutUrl);

            // Launch browser to hosted checkout page
            Application.OpenURL(checkoutUrl);

            // Begin polling order status
            StartPollingOrderStatus(_activeOrderId);
        }

        private void OnApplicationFocus(bool hasFocus)
        {
            // When player switches back to Unity from the payment browser, immediately check status
            if (hasFocus && !string.IsNullOrEmpty(_activeOrderId))
            {
                Debug.Log("[PaymentManager] App regained focus. Polling active order status...");
                CheckOrderStatusOnce(_activeOrderId);
            }
        }

        private void StartPollingOrderStatus(string orderId)
        {
            _pollingCts?.Cancel();
            _pollingCts = new CancellationTokenSource();
            StartCoroutine(PollOrderStatusRoutine(orderId, _pollingCts.Token));
        }

        private IEnumerator PollOrderStatusRoutine(string orderId, CancellationToken ct)
        {
            int maxAttempts = 40; // 40 * 1.5s = 60s max
            for (int i = 0; i < maxAttempts; i++)
            {
                if (ct.IsCancellationRequested) yield break;

                yield return new WaitForSeconds(1.5f);

                Task<ApiResponse<PaymentOrderDto>> task = RailboundApiClient.Instance.GetAsync<PaymentOrderDto>(
                    $"/payments/orders/{orderId}", ct);

                while (!task.IsCompleted)
                {
                    yield return null;
                }

                if (task.Result.IsSuccess && task.Result.Data != null)
                {
                    string status = task.Result.Data.status;
                    Debug.Log($"[PaymentManager] Polling order {orderId}: {status}");

                    if (status == "COMPLETED")
                    {
                        Debug.Log($"[PaymentManager] Order {orderId} COMPLETED! Refreshing authoritative wallet balance...");
                        _activeOrderId = null;
                        OnPaymentCompleted?.Invoke(orderId);
                        RefreshWalletBalance();
                        yield break;
                    }
                    else if (status == "FAILED" || status == "CANCELLED" || status == "EXPIRED")
                    {
                        _activeOrderId = null;
                        OnPaymentFailed?.Invoke(orderId, status);
                        yield break;
                    }
                }
            }

            Debug.LogWarning($"[PaymentManager] Polling timed out for order {orderId}. Continuing in background.");
        }

        private async void CheckOrderStatusOnce(string orderId)
        {
            var res = await RailboundApiClient.Instance.GetAsync<PaymentOrderDto>($"/payments/orders/{orderId}");
            if (res.IsSuccess && res.Data?.status == "COMPLETED")
            {
                _activeOrderId = null;
                OnPaymentCompleted?.Invoke(orderId);
                RefreshWalletBalance();
            }
        }

        public async void RefreshWalletBalance()
        {
            var res = await RailboundApiClient.Instance.GetAsync<WalletDto>("/wallet/me");
            if (res.IsSuccess && res.Data != null)
            {
                Debug.Log($"[PaymentManager] Authoritative Real Coin balance: {res.Data.realCoins}");
                OnWalletUpdated?.Invoke(res.Data.realCoins);
            }
        }
    }

    [Serializable]
    public class CheckoutSessionDto
    {
        public string orderId;
        public string status;
        public string checkoutUrl;
        public string bundleSku;
        public long coinsToCredit;
        public long amountMinor;
        public string currency;
    }

    [Serializable]
    public class PaymentOrderDto
    {
        public string orderId;
        public string status;
        public string bundleSku;
        public long coinsToCredit;
        public long amountMinor;
    }

    [Serializable]
    public class WalletDto
    {
        public string userId;
        public long realCoins;
        public long gameCoins;
    }
}
