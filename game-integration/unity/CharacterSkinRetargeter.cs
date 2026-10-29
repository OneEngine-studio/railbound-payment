using UnityEngine;

namespace Railbound.Economy
{
    /// <summary>
    /// Retargets operator meshes and materials based on authorized cosmetic entitlements.
    /// </summary>
    public class CharacterSkinRetargeter : MonoBehaviour
    {
        [Header("Mesh Renderers")]
        [SerializeField] private SkinnedMeshRenderer characterMeshRenderer;

        public void ApplySkin(string skinSku)
        {
            Debug.Log($"[CharacterSkinRetargeter] Retargeting character skin to: {skinSku}");
            // In runtime build with Addressables, asset bundle mesh/material is loaded
        }
    }
}
