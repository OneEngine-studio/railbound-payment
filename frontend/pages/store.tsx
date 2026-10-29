import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { Coins, Check, AlertCircle, Sparkles, Filter, X } from "lucide-react";

export default function Store() {
  const { wallet, refreshWallet } = useAuth();
  const [items, setItems] = useState<any[]>([]);
  const [ownedSkus, setOwnedSkus] = useState<Set<string>>(new Set());
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [loading, setLoading] = useState(true);

  // Purchase Modal State
  const [activeModalItem, setActiveModalItem] = useState<any | null>(null);
  const [purchasing, setPurchasing] = useState(false);
  const [purchaseSuccess, setPurchaseSuccess] = useState<string | null>(null);
  const [purchaseError, setPurchaseError] = useState<string | null>(null);

  const categories = ["ALL", "OPERATOR_SKIN", "ATTACHMENT", "WEAPON_CAMO"];

  const loadData = async () => {
    try {
      setLoading(true);
      const cat = selectedCategory === "ALL" ? undefined : selectedCategory;
      const [itemsData, inventoryData] = await Promise.all([
        api.getShopItems(cat),
        api.getMyInventory().catch(() => []),
      ]);
      setItems(itemsData);
      setOwnedSkus(new Set(inventoryData.map((ent: any) => ent.itemSku)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [selectedCategory]);

  const handlePurchase = async () => {
    if (!activeModalItem) return;
    setPurchasing(true);
    setPurchaseError(null);
    setPurchaseSuccess(null);

    const idempotencyKey = "web_pur_" + Math.random().toString(36).substring(2, 12);
    try {
      const res = await api.purchaseItem(activeModalItem.sku, idempotencyKey);
      setPurchaseSuccess(`Successfully acquired ${activeModalItem.title}!`);
      await refreshWallet();
      setOwnedSkus((prev) => new Set([...Array.from(prev), activeModalItem.sku]));
      setTimeout(() => {
        setActiveModalItem(null);
        setPurchaseSuccess(null);
      }, 1500);
    } catch (err: any) {
      setPurchaseError(err.message || "Purchase could not be completed.");
    } finally {
      setPurchasing(false);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Header & Filter Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Armory Store</h1>
          <p className="text-xs text-zinc-400 mt-1">
            Authoritative cosmetic drops and precision weapon attachments. Acquired with Real Coins.
          </p>
        </div>

        {/* Category Pills */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`rounded-md px-3 py-1.5 text-xs font-mono transition-colors whitespace-nowrap ${
                selectedCategory === cat
                  ? "bg-zinc-200 text-zinc-950 font-semibold"
                  : "bg-zinc-900 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800"
              }`}
            >
              {cat.replace("_", " ")}
            </button>
          ))}
        </div>
      </div>

      {/* Items Grid */}
      {loading ? (
        <div className="py-20 text-center text-xs font-mono text-zinc-500">Loading catalog items...</div>
      ) : items.length === 0 ? (
        <div className="py-20 text-center text-xs text-zinc-500">No items available in this category.</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {items.map((item) => {
            const isOwned = ownedSkus.has(item.sku);
            const canAfford = wallet ? wallet.realCoins >= item.realCoinPrice : false;

            return (
              <div
                key={item.sku}
                className="group flex flex-col justify-between rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-white/[0.18] transition-all"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="rounded bg-zinc-800/80 px-2 py-0.5 text-[10px] font-mono text-zinc-400 uppercase tracking-wider">
                      {item.category?.replace("_", " ")}
                    </span>
                    {isOwned && (
                      <span className="inline-flex items-center gap-1 rounded bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-mono text-emerald-400 font-semibold">
                        <Check className="h-3 w-3" />
                        OWNED
                      </span>
                    )}
                  </div>

                  <h3 className="text-base font-bold text-zinc-100">{item.title}</h3>
                  <p className="mt-2 text-xs text-zinc-400 line-clamp-2 leading-relaxed">
                    {item.description}
                  </p>

                  {/* Compatibility info */}
                  {item.compatibility?.slot && (
                    <div className="mt-3 text-[10px] font-mono text-zinc-500">
                      Slot: <span className="text-zinc-300">{item.compatibility.slot}</span>
                    </div>
                  )}
                </div>

                <div className="mt-6 pt-4 border-t border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-1">
                    <Coins className="h-4 w-4 text-amber-400" />
                    <span className="text-base font-bold font-mono text-amber-300">
                      {item.realCoinPrice.toLocaleString()}
                    </span>
                    <span className="text-[10px] font-mono text-amber-400/70 ml-0.5">RC</span>
                  </div>

                  {isOwned ? (
                    <Link
                      href="/inventory"
                      className="rounded-md border border-white/10 px-3 py-1.5 text-xs text-zinc-400 hover:text-white transition-colors"
                    >
                      View in Inventory
                    </Link>
                  ) : (
                    <button
                      onClick={() => setActiveModalItem(item)}
                      className="rounded-md bg-amber-500 px-3 py-1.5 text-xs font-semibold text-black hover:bg-amber-400 transition-colors"
                    >
                      Acquire
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Minimalist Purchase Modal */}
      {activeModalItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-2xl border border-white/10 bg-[#141418] p-6 shadow-2xl">
            <div className="flex items-center justify-between pb-3 border-b border-white/5">
              <h2 className="text-sm font-semibold text-zinc-200 uppercase font-mono">Confirm Armory Acquisition</h2>
              <button
                onClick={() => setActiveModalItem(null)}
                className="text-zinc-500 hover:text-white transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="mt-4">
              <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400 uppercase">
                {activeModalItem.category}
              </span>
              <h3 className="mt-2 text-lg font-bold text-zinc-100">{activeModalItem.title}</h3>
              <p className="mt-1 text-xs text-zinc-400">{activeModalItem.description}</p>
            </div>

            {/* Financial Breakdown */}
            <div className="mt-6 rounded-lg border border-white/5 bg-zinc-950 p-3.5 space-y-2 text-xs font-mono">
              <div className="flex justify-between text-zinc-400">
                <span>Current Real Coins:</span>
                <span className="text-zinc-200">{wallet?.realCoins.toLocaleString() ?? 0} RC</span>
              </div>
              <div className="flex justify-between text-amber-400">
                <span>Item Price:</span>
                <span className="font-semibold">- {activeModalItem.realCoinPrice.toLocaleString()} RC</span>
              </div>
              <div className="border-t border-white/5 pt-2 flex justify-between font-bold text-zinc-200">
                <span>Balance After Purchase:</span>
                <span
                  className={
                    (wallet?.realCoins ?? 0) - activeModalItem.realCoinPrice < 0
                      ? "text-rose-400"
                      : "text-emerald-400"
                  }
                >
                  {((wallet?.realCoins ?? 0) - activeModalItem.realCoinPrice).toLocaleString()} RC
                </span>
              </div>
            </div>

            {/* Error Banner */}
            {purchaseError && (
              <div className="mt-4 flex items-start gap-2 rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">
                <AlertCircle className="h-4 w-4 shrink-0 text-rose-400 mt-0.5" />
                <div className="flex-1">
                  <p>{purchaseError}</p>
                  {(wallet?.realCoins ?? 0) < activeModalItem.realCoinPrice && (
                    <Link href="/top-up" className="mt-2 inline-block font-semibold text-amber-400 underline">
                      Top-up Real Coins now &rarr;
                    </Link>
                  )}
                </div>
              </div>
            )}

            {/* Success Banner */}
            {purchaseSuccess && (
              <div className="mt-4 flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300">
                <Check className="h-4 w-4 text-emerald-400" />
                <span>{purchaseSuccess}</span>
              </div>
            )}

            {/* Modal Actions */}
            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setActiveModalItem(null)}
                className="rounded-md px-3.5 py-2 text-xs text-zinc-400 hover:text-white transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={purchasing || (wallet?.realCoins ?? 0) < activeModalItem.realCoinPrice}
                onClick={handlePurchase}
                className="rounded-md bg-amber-500 px-4 py-2 text-xs font-semibold text-black hover:bg-amber-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {purchasing ? "Authorizing..." : "Confirm & Deduct Coins"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
