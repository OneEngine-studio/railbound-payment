import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../lib/api";
import { Check, ShieldCheck, Crosshair, ArrowRight, X } from "lucide-react";

export default function InventoryPage() {
  const [entitlements, setEntitlements] = useState<any[]>([]);
  const [catalogMap, setCatalogMap] = useState<Record<string, any>>({});
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const loadInventory = async () => {
    try {
      setLoading(true);
      const [inv, items] = await Promise.all([
        api.getMyInventory(),
        api.getShopItems(),
      ]);
      setEntitlements(inv);

      const map: Record<string, any> = {};
      items.forEach((it: any) => {
        map[it.sku] = it;
      });
      setCatalogMap(map);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInventory();
  }, []);

  const handleEquip = async (sku: string, slot: string) => {
    setActionLoading(sku);
    try {
      await api.equipItem(sku, slot || "PRIMARY");
      await loadInventory();
    } catch (err: any) {
      alert("Equip failed: " + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleUnequip = async (slot: string, sku: string) => {
    setActionLoading(sku);
    try {
      await api.unequipItem(slot);
      await loadInventory();
    } catch (err: any) {
      alert("Unequip failed: " + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">In-Game Armory Inventory</h1>
          <p className="text-xs text-zinc-400 mt-1">
            Authoritative player entitlements synchronized directly with the Unity 2023.1 game engine.
          </p>
        </div>

        <Link
          href="/store"
          className="inline-flex items-center gap-1.5 rounded-lg border border-white/10 bg-zinc-900 px-4 py-2 text-xs font-medium text-zinc-200 hover:bg-zinc-800 transition-colors self-start sm:self-auto"
        >
          Browse Store <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      {loading ? (
        <div className="py-20 text-center text-xs font-mono text-zinc-500">Loading player entitlements...</div>
      ) : entitlements.length === 0 ? (
        <div className="py-20 text-center rounded-2xl border border-white/5 bg-[#121215] p-8 max-w-md mx-auto">
          <Crosshair className="mx-auto h-10 w-10 text-zinc-600 mb-3" />
          <h2 className="text-sm font-semibold text-zinc-200">No Armory Items Owned Yet</h2>
          <p className="mt-1 text-xs text-zinc-500 mb-6">
            Acquire cosmetic attachments or operator skins in the Armory Store using Real Coins.
          </p>
          <Link
            href="/store"
            className="rounded-lg bg-amber-500 px-4 py-2 text-xs font-semibold text-black hover:bg-amber-400 transition-colors"
          >
            Visit Armory Store
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {entitlements.map((ent) => {
            const item = catalogMap[ent.itemSku] || {};
            const isEquipped = !!ent.equippedSlot;
            const slot = ent.equippedSlot || item.compatibility?.slot || "EQUIPMENT";

            return (
              <div
                key={ent.entitlementId}
                className="flex flex-col justify-between rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-white/[0.16] transition-all"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400 uppercase">
                      {item.category?.replace("_", " ") || "COSMETIC"}
                    </span>
                    {isEquipped ? (
                      <span className="inline-flex items-center gap-1 rounded bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-mono text-emerald-400 font-semibold">
                        <Check className="h-3 w-3" />
                        EQUIPPED ({ent.equippedSlot})
                      </span>
                    ) : (
                      <span className="rounded bg-zinc-900 border border-white/5 px-2 py-0.5 text-[10px] font-mono text-zinc-500">
                        IN STASH
                      </span>
                    )}
                  </div>

                  <h3 className="text-base font-bold text-zinc-100">{item.title || ent.itemSku}</h3>
                  <p className="mt-1.5 text-xs text-zinc-400 line-clamp-2">
                    {item.description || "Authoritative game asset entitlement."}
                  </p>

                  <div className="mt-4 rounded border border-white/5 bg-zinc-950 p-2.5 space-y-1 text-[10px] font-mono text-zinc-500">
                    <div>SKU: <span className="text-zinc-300">{ent.itemSku}</span></div>
                    <div>Acquired: <span className="text-zinc-400">{new Date(ent.acquiredAt).toLocaleDateString()}</span></div>
                  </div>
                </div>

                <div className="mt-6 pt-4 border-t border-white/5 flex items-center justify-between">
                  {isEquipped ? (
                    <button
                      onClick={() => handleUnequip(ent.equippedSlot, ent.itemSku)}
                      disabled={actionLoading === ent.itemSku}
                      className="w-full rounded-md border border-rose-500/30 bg-rose-500/10 py-1.5 text-xs font-semibold text-rose-300 hover:bg-rose-500/20 transition-colors"
                    >
                      {actionLoading === ent.itemSku ? "Unequipping..." : "Unequip from Loadout"}
                    </button>
                  ) : (
                    <button
                      onClick={() => handleEquip(ent.itemSku, item.compatibility?.slot || "PRIMARY")}
                      disabled={actionLoading === ent.itemSku}
                      className="w-full rounded-md bg-zinc-200 py-1.5 text-xs font-semibold text-zinc-950 hover:bg-white transition-colors"
                    >
                      {actionLoading === ent.itemSku ? "Equipping..." : `Equip to ${slot}`}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
