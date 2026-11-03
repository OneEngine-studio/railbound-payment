import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";
import { api, PAYMENT_PROVIDER, MOCK_PAYMENTS_ENABLED } from "../lib/api";
import { useAuth } from "../lib/auth";
import { Coins, ShieldCheck, Zap, ArrowRight, Lock, Check } from "lucide-react";

export default function TopUp() {
  const router = useRouter();
  const { user } = useAuth();
  const [bundles, setBundles] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [initiatingSku, setInitiatingSku] = useState<string | null>(null);
  const [provider, setProvider] = useState(PAYMENT_PROVIDER);
  const [gatewayError, setGatewayError] = useState<string | null>(null);

  useEffect(() => {
    api.getBundles()
      .then(setBundles)
      .finally(() => setLoading(false));
  }, []);

  const handleSelectBundle = async (bundleSku: string) => {
    setInitiatingSku(bundleSku);
    setGatewayError(null);
    try {
      const idempotencyKey = "web_topup_" + Math.random().toString(36).substring(2, 12);
      const session = await api.createCheckoutSession(bundleSku, provider, idempotencyKey);

      // Hosted checkout bridge: for PayHere this is the frontend page that
      // auto-submits the signed form to the gateway.
      if (session.checkoutUrl) {
        window.location.href = session.checkoutUrl;
      }
    } catch (err: any) {
      setGatewayError(err.message || "Failed to initiate checkout session");
      setInitiatingSku(null);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      {/* Header */}
      <div className="max-w-2xl mb-10">
        <div className="inline-flex items-center gap-2 rounded-full border border-amber-500/30 bg-amber-500/10 px-3 py-1 text-[11px] font-mono text-amber-400 mb-3">
          <ShieldCheck className="h-3.5 w-3.5" />
          <span>HOSTED CHECKOUT GATEWAY // PCI-SAFE</span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-zinc-100">Top-Up Real Coins</h1>
        <p className="mt-2 text-sm text-zinc-400 leading-relaxed">
          Select a verified Real Coin bundle. Coins are authoritatively minted upon cryptographic gateway callback fulfillment. Never wait for manual intervention.
        </p>

        <div className="mt-5 flex flex-wrap items-center gap-3">
          <span className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-1.5 text-[11px] font-mono text-emerald-300">
            <ShieldCheck className="h-3.5 w-3.5" />
            PayHere secure hosted checkout
          </span>

          {MOCK_PAYMENTS_ENABLED && (
            <label className="inline-flex items-center gap-2 text-[11px] font-mono text-zinc-400">
              <span>Gateway:</span>
              <select
                value={provider}
                onChange={(e) => setProvider(e.target.value)}
                className="rounded-md border border-white/10 bg-zinc-950 px-2 py-1 text-zinc-200 focus:border-amber-500 focus:outline-none"
              >
                <option value="payhere">PayHere (sandbox/live)</option>
                <option value="mock">Mock (development)</option>
              </select>
            </label>
          )}
        </div>

        {gatewayError && (
          <div className="mt-4 rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-300">
            {gatewayError}
          </div>
        )}
      </div>

      {/* Bundles Grid */}
      {loading ? (
        <div className="py-20 text-center text-xs font-mono text-zinc-500">Loading coin bundles...</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {bundles.map((bundle) => {
            const isSelected = initiatingSku === bundle.sku;
            return (
              <div
                key={bundle.sku}
                className="relative flex flex-col justify-between rounded-2xl border border-white/[0.08] bg-[#121215] p-6 hover:border-white/[0.18] transition-all"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="text-xs font-mono text-zinc-400 uppercase tracking-wider">
                      {bundle.sku}
                    </span>
                    {bundle.badgeText && (
                      <span className="rounded bg-amber-500/15 border border-amber-500/30 px-2 py-0.5 text-[10px] font-mono font-semibold text-amber-400">
                        {bundle.badgeText}
                      </span>
                    )}
                  </div>

                  <h2 className="text-lg font-bold text-zinc-100">{bundle.title}</h2>

                  {/* Coin Amount Display */}
                  <div className="mt-5 flex items-baseline gap-2">
                    <span className="text-4xl font-extrabold font-mono text-amber-400">
                      {bundle.totalCoins.toLocaleString()}
                    </span>
                    <span className="text-xs font-mono text-zinc-400">RC</span>
                  </div>

                  {/* Breakdown */}
                  <div className="mt-3 space-y-1 text-xs font-mono text-zinc-400">
                    <div className="flex justify-between">
                      <span>Base Coins:</span>
                      <span className="text-zinc-200">{bundle.baseCoins.toLocaleString()}</span>
                    </div>
                    {bundle.bonusCoins > 0 ? (
                      <div className="flex justify-between text-emerald-400 font-semibold">
                        <span>Bonus Coins:</span>
                        <span>+{bundle.bonusCoins.toLocaleString()}</span>
                      </div>
                    ) : (
                      <div className="flex justify-between text-zinc-600">
                        <span>Bonus Coins:</span>
                        <span>0</span>
                      </div>
                    )}
                  </div>
                </div>

                {/* Price & Checkout CTA */}
                <div className="mt-8 pt-5 border-t border-white/5">
                  <div className="flex items-center justify-between mb-4">
                    <span className="text-xs text-zinc-400">Price ({bundle.currency || "USD"}):</span>
                    <span className="text-lg font-bold font-mono text-zinc-100">
                      {((bundle.fiatAmountMinor || 0) / 100).toFixed(2)}
                    </span>
                  </div>

                  <button
                    onClick={() => handleSelectBundle(bundle.sku)}
                    disabled={isSelected}
                    className="w-full flex items-center justify-center gap-2 rounded-lg bg-amber-500 py-2.5 text-xs font-semibold text-black hover:bg-amber-400 disabled:opacity-50 transition-colors"
                  >
                    {isSelected ? (
                      <span>Connecting Gateway...</span>
                    ) : (
                      <>
                        <Lock className="h-3.5 w-3.5" />
                        <span>Proceed to Checkout</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Security & Invariant Disclosures */}
      <div className="mt-16 rounded-xl border border-white/5 bg-zinc-950/50 p-6 text-xs text-zinc-500 leading-relaxed space-y-2">
        <div className="flex items-center gap-2 font-semibold text-zinc-300">
          <ShieldCheck className="h-4 w-4 text-emerald-400" />
          <span>Payment Security & Fair Play Policy</span>
        </div>
        <p>
          1. <strong>Authoritative Server Fulfillment:</strong> Real Coins are authoritatively minted in your wallet only after the payment gateway's cryptographic checksum is verified and the amount/currency match the immutable order snapshot. Redirect URLs never grant currency.
        </p>
        <p>
          2. <strong>Zero Card Exposure:</strong> All card credentials and transactions are handled on PayHere's PCI-DSS hosted checkout pages. Railbound servers never process or store raw payment details.
        </p>
        <p>
          3. <strong>Single-Credit Guarantee:</strong> Unique provider-event and ledger reference constraints prevent double-crediting on network retries, replayed callbacks or duplicate payments.
        </p>
      </div>
    </div>
  );
}
