using UnityEngine;
using UnityEngine.UI;

namespace Railbound.Economy
{
    /// <summary>
    /// Controls the in-game lobby HUD top bar.
    /// Invariant: Displays strictly server-sourced Real Coins. Game Coins placeholder remains disabled.
    /// Suggested Location: Existing Lobby UI / Assets/Project/Scripts/UI/LobbyUIController.cs
    /// </summary>
    public class LobbyUIController : MonoBehaviour
    {
        [Header("HUD Elements")]
        [SerializeField] private Text realCoinsText;
        [SerializeField] private Button topUpPlusButton;
        
        [Header("Soft Progression Currency (Reserved)")]
        [SerializeField] private Text gameCoinsText;
        [SerializeField] private GameObject gameCoinsContainer;

        [Header("Shop Dialog Reference")]
        [SerializeField] private LobbyShopUI lobbyShopUI;

        private void Start()
        {
            if (topUpPlusButton != null)
            {
                topUpPlusButton.onClick.AddListener(OnTopUpPlusClicked);
            }

            // Game Coins deferred per currency policy: disable interaction
            if (gameCoinsContainer != null)
            {
                gameCoinsContainer.SetActive(false);
            }
            if (gameCoinsText != null)
            {
                gameCoinsText.text = "--";
            }

            if (PaymentManager.Instance != null)
            {
                PaymentManager.Instance.OnWalletUpdated += UpdateRealCoinsDisplay;
                PaymentManager.Instance.RefreshWalletBalance();
            }
        }

        private void OnDestroy()
        {
            if (PaymentManager.Instance != null)
            {
                PaymentManager.Instance.OnWalletUpdated -= UpdateRealCoinsDisplay;
            }
        }

        private void OnTopUpPlusClicked()
        {
            Debug.Log("[LobbyUIController] '+' Top-Up button pressed. Opening Shop Top-Up modal...");
            if (lobbyShopUI != null)
            {
                lobbyShopUI.OpenTopUpTab();
            }
            else
            {
                // Fallback: initiate direct PDF example top-up
                PaymentManager.Instance?.StartTopUp("bundle_rc_1000");
            }
        }

        public void UpdateRealCoinsDisplay(long newBalance)
        {
            if (realCoinsText != null)
            {
                realCoinsText.text = newBalance.ToString("N0");
            }
            Debug.Log($"[LobbyUIController] Updated Real Coin HUD: {newBalance}");
        }
    }
}
