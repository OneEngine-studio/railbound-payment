using System.Collections.Generic;
using UnityEngine;

namespace Railbound.Economy
{
    /// <summary>
    /// Represents a weapon with modular attachment sockets (OPTIC, BARREL, MAGAZINE, UNDERBARREL).
    /// </summary>
    public class Weapon : MonoBehaviour
    {
        [Header("Weapon Configuration")]
        [SerializeField] private string weaponSku = "wp_m4a1";

        [Header("Attachment Socket Transforms")]
        [SerializeField] private Transform opticSocket;
        [SerializeField] private Transform barrelSocket;
        [SerializeField] private Transform magazineSocket;
        [SerializeField] private Transform underbarrelSocket;

        private readonly Dictionary<string, string> _equippedAttachments = new();

        public void MountAttachment(string slot, string attachmentSku)
        {
            _equippedAttachments[slot] = attachmentSku;
            Transform targetSocket = GetSocketForSlot(slot);

            if (targetSocket == null)
            {
                Debug.LogWarning($"[Weapon] No socket transform configured for slot: {slot}");
                return;
            }

            // Remove existing mesh in socket
            foreach (Transform child in targetSocket)
            {
                Destroy(child.gameObject);
            }

            Debug.Log($"[Weapon] Mounted {attachmentSku} to {weaponSku} socket {slot}.");
        }

        private Transform GetSocketForSlot(string slot) => slot switch
        {
            "OPTIC" => opticSocket,
            "BARREL" => barrelSocket,
            "MAGAZINE" => magazineSocket,
            "UNDERBARREL" => underbarrelSocket,
            _ => null
        };
    }
}
