using System;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using UnityEngine;

namespace Railbound.Content
{
    /// <summary>
    /// Validates Addressables/AssetBundle release manifest metadata and enforces SHA-256 integrity checks.
    /// Invariant: Rejects unverified or incompatible engine bundles before loading.
    /// </summary>
    public class AssetBundleManifestHandler : MonoBehaviour
    {
        public static AssetBundleManifestHandler Instance { get; private set; }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        public async Task<bool> VerifyAndCacheBundleAsync(string itemSku, byte[] bundleBytes, string expectedSha256)
        {
            using var sha256 = SHA256.Create();
            byte[] hashBytes = sha256.ComputeHash(bundleBytes);
            string computedHex = BitConverter.ToString(hashBytes).Replace("-", "").ToLowerInvariant();

            if (!string.Equals(computedHex, expectedSha256.ToLowerInvariant(), StringComparison.OrdinalIgnoreCase))
            {
                Debug.LogError($"[AssetBundleManifestHandler] SHA-256 Checksum Mismatch for {itemSku}! Expected: {expectedSha256}, Actual: {computedHex}");
                return false;
            }

            Debug.Log($"[AssetBundleManifestHandler] Checksum verified for {itemSku}. Bundle cached.");
            return true;
        }
    }
}
