using System;
using UnityEngine;

namespace Railbound.Economy
{
    /// <summary>
    /// Bridges authoritative server entitlements with runtime weapon attachments and operator skin meshes.
    /// Invariant: In-game equipment is only applied if the server verifies active ownership.
    /// Suggested Location: Assets/Project/Scripts/Economy/ArmoryEquipBridge.cs
    /// </summary>
    public class ArmoryEquipBridge : MonoBehaviour
    {
        public static ArmoryEquipBridge Instance { get; private set; }

        [Header("Runtime Subsystems")]
        [SerializeField] private Weapon activeWeapon;
        [SerializeField] private CharacterSkinRetargeter skinRetargeter;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        public async void EquipCosmeticOrAttachment(string itemSku, string slot, string weaponSku = null)
        {
            Debug.Log($"[ArmoryEquipBridge] Requesting equip authorization for {itemSku} into slot {slot}...");

            string payload = $"{{\"itemSku\":\"{itemSku}\",\"slot\":\"{slot}\",\"weaponSku\":\"{weaponSku}\"}}";
            var res = await RailboundApiClient.Instance.PostAsync<EquipResponseDto>("/inventory/me/equip", payload);

            if (!res.IsSuccess || res.Data == null)
            {
                Debug.LogError($"[ArmoryEquipBridge] Equip rejected by server: {res.ErrorMessage}");
                return;
            }

            Debug.Log($"[ArmoryEquipBridge] Server approved equip for {res.Data.itemSku} into slot {res.Data.equippedSlot}.");

            // Apply in-game visual change
            if (slot == "OPERATOR" && skinRetargeter != null)
            {
                skinRetargeter.ApplySkin(itemSku);
            }
            else if (activeWeapon != null)
            {
                activeWeapon.MountAttachment(slot, itemSku);
            }
        }
    }

    [Serializable]
    public class EquipResponseDto
    {
        public string entitlementId;
        public string itemSku;
        public string state;
        public string equippedSlot;
    }
}
