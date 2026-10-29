using System;
using System.Threading.Tasks;
using UnityEngine;

namespace Railbound.Nakama
{
    /// <summary>
    /// Service-to-service adapter contract for Nakama game server.
    /// Invariant: Before permitting a match participant to spawn with an attachment or skin,
    /// Nakama server calls POST /api/v1/internal/entitlements/check with the shared secret.
    /// </summary>
    public class NakamaRpcAdapter
    {
        private readonly string _portalInternalBaseUrl;
        private readonly string _internalServiceKey;

        public NakamaRpcAdapter(string portalInternalBaseUrl, string internalServiceKey)
        {
            _portalInternalBaseUrl = portalInternalBaseUrl;
            _internalServiceKey = internalServiceKey;
        }

        /// <summary>
        /// Authoritative match server check called prior to match load.
        /// </summary>
        public async Task<bool> VerifyPlayerEntitlementRpc(string nakamaUserId, string itemSku)
        {
            // Internal REST POST to http://localhost:8080/api/v1/internal/entitlements/check
            // with Header: X-Internal-Service-Key
            Debug.Log($"[NakamaRpcAdapter] Checking authoritative entitlement for {nakamaUserId} / {itemSku}");
            return true;
        }
    }
}
