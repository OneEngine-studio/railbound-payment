import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";
import Link from "next/link";
import { api } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import { Coins, Check, ArrowLeft, Shield, AlertCircle } from "lucide-react";

export default function ItemDetail() {
  const router = useRouter();
  const { sku } = router.query;
  const { wallet, refreshWallet } = useAuth();

  const [item, setItem] = useState<any | null>(null);
  const [isOwned, setIsOwned] = useState(false);
  const [purchasing, setPurchasing] = useState(false);
  const [feedback, setFeedback] = useState<{ type: "success" | "error"; message: string } | null>(null);

  useEffect(() => {
    if (!sku) return;
    const load = async () => {
      try {
        const itemData = await api.getShopItem(sku as string);
        setItem(itemData);
        const inv = await api.getMyInventory();
        setIsOwned(inv.some((ent: any) => ent.itemSku === sku));
      } catch (err: any) {
        setFeedback({ type: "error", message: err.message || "Failed to load item" });
      }
    };
    load();
  }, [sku]);

  const handlePurchase = async () => {
    if (!item) return;
    setPurchasing(true);
    setFeedback(null);
    try {
      const idempotencyKey = "web_pur_dtl_" + Math.random().toString(36).substring(2, 12);
      await api.purchaseItem(item.sku, idempotencyKey);
      setFeedback({ type: "success", message: `Successfully acquired ${item.title}!` });
      setIsOwned(true);
      await refreshWallet();
    } catch (err: any) {
      setFeedback({ type: "error", message: err.message || "Purchase failed." });
    } finally {
      setPurchasing(false);
    }
  };

  if (!item) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-16 text-center text-xs font-mono text-zinc-500">
        Loading armory asset...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <Link
        href="/store"
        className="inline-flex items-center gap-2 text-xs font-mono text-zinc-400 hover:text-white mb-6 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to Armory Store
      </Link>

      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 sm:p-10">
        <div className="flex flex-col md:flex-row gap-8 items-start">
          {/* Visual Placeholder / Mock 3D Canvas */}
          <div className="w-full md:w-1/2 aspect-square rounded-xl border border-white/5 bg-zinc-950 flex flex-col items-center justify-center p-6 text-center relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-t from-zinc-950 via-transparent to-transparent" />
            <div className="relative z-10 flex flex-col items-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-zinc-900 border border-white/10 text-amber-400 mb-4">
                <Coins className="h-8 w-8" />
              </div>
              <span className="text-xs font-mono uppercase text-zinc-400">{item.sku}</span>
              <span className="text-[10px] text-zinc-600 mt-1 max-w-[200px]">
                Unity 3D preview rendered via game client Addressables pipeline.
              </span>
            </div>
          </div>

          {/* Details & Specs */}
          <div className="w-full md:w-1/2 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2 mb-2">
                <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400 uppercase">
                  {item.category?.replace("_", " ")}
                </span>
                <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400">
                  v{item.currentContentVersion}
                </span>
              </div>

              <h1 className="text-2xl font-bold text-zinc-100">{item.title}</h1>
              <p className="mt-3 text-sm text-zinc-400 leading-relaxed">{item.description}</p>

              {/* Compatibility Specification */}
              <div className="mt-6 rounded-lg border border-white/5 bg-zinc-950/60 p-4 space-y-2 text-xs font-mono">
                <div className="text-[10px] uppercase text-zinc-500 font-semibold tracking-wider">
                  Engine Specifications
                </div>
                <div className="flex justify-between text-zinc-400">
                  <span>Target Slot:</span>
                  <span className="text-zinc-200">{item.compatibility?.slot || "N/A"}</span>
                </div>
                <div className="flex justify-between text-zinc-400">
                  <span>Min Client Version:</span>
                  <span className="text-zinc-200">{item.compatibility?.minimumClientVersion || "1.0.0"}</span>
                </div>
                {item.compatibility?.supportedWeapons && (
                  <div className="flex justify-between text-zinc-400">
                    <span>Compatible Bases:</span>
                    <span className="text-zinc-200">{item.compatibility.supportedWeapons.join(", ")}</span>
                  </div>
                )}
              </div>
            </div>

            {/* Pricing & CTA */}
            <div className="mt-8 pt-6 border-t border-white/5">
              <div className="flex items-center justify-between mb-4">
                <span className="text-xs text-zinc-400">Acquisition Price:</span>
                <div className="flex items-center gap-1.5">
                  <Coins className="h-5 w-5 text-amber-400" />
                  <span className="text-2xl font-bold font-mono text-amber-300">
                    {item.realCoinPrice.toLocaleString()}
                  </span>
                  <span className="text-xs font-mono text-amber-400/80">REAL COINS</span>
                </div>
              </div>

              {feedback && (
                <div
                  className={`mb-4 flex items-center gap-2 rounded-lg p-3 text-xs ${
                    feedback.type === "success"
                      ? "border border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                      : "border border-rose-500/30 bg-rose-500/10 text-rose-300"
                  }`}
                >
                  {feedback.type === "success" ? <Check className="h-4 w-4" /> : <AlertCircle className="h-4 w-4" />}
                  <span>{feedback.message}</span>
                </div>
              )}

              {isOwned ? (
                <div className="flex items-center justify-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/30 py-3 text-xs font-semibold text-emerald-400">
                  <Check className="h-4 w-4" />
                  Item is Owned & Ready in In-Game Armory
                </div>
              ) : (
                <button
                  onClick={handlePurchase}
                  disabled={purchasing || (wallet?.realCoins ?? 0) < item.realCoinPrice}
                  className="w-full rounded-lg bg-amber-500 py-3 text-xs font-semibold text-black hover:bg-amber-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  {purchasing
                    ? "Authorizing Purchase..."
                    : (wallet?.realCoins ?? 0) < item.realCoinPrice
                    ? "Insufficient Real Coins (Top-Up Required)"
                    : `Acquire ${item.title}`}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
