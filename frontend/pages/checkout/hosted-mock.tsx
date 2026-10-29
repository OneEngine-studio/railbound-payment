import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";
import { api } from "../../lib/api";
import { Lock, ShieldCheck, CreditCard, ArrowRight, X } from "lucide-react";

export default function HostedMockCheckout() {
  const router = useRouter();
  const { orderId } = router.query;

  const [order, setOrder] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!orderId) return;
    api.getOrder(orderId as string)
      .then(setOrder)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [orderId]);

  const handlePay = async () => {
    if (!order) return;
    setProcessing(true);
    setError(null);

    try {
      // Trigger signed mock webhook fulfillment on backend
      await api.mockCompleteOrder(order.orderId);
      
      // Redirect to authoritative return polling page
      router.push(`/checkout/return?orderId=${order.orderId}`);
    } catch (err: any) {
      setError(err.message || "Payment simulation failed");
      setProcessing(false);
    }
  };

  if (loading) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center text-xs font-mono text-zinc-500">
        Connecting to hosted payment gateway...
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <p className="text-sm text-rose-400 mb-4">{error || "Order session not found."}</p>
        <button
          onClick={() => router.push("/top-up")}
          className="rounded-md bg-zinc-800 px-4 py-2 text-xs text-zinc-200"
        >
          Return to Top-Up
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-12 sm:px-6">
      <div className="rounded-2xl border border-white/10 bg-[#141418] p-6 sm:p-8 shadow-2xl">
        {/* Hosted Gateway Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/5 mb-6">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded bg-amber-500/20 text-amber-400">
              <Lock className="h-4 w-4" />
            </div>
            <div>
              <div className="text-xs font-bold text-zinc-100 uppercase tracking-wider">
                Mock Provider Hosted Checkout
              </div>
              <div className="text-[10px] font-mono text-zinc-500">Sandboxed PCI Environment</div>
            </div>
          </div>
          <button
            onClick={() => router.push("/top-up")}
            className="text-zinc-500 hover:text-white transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Order Summary Snapshot */}
        <div className="rounded-xl border border-white/5 bg-zinc-950 p-4 mb-6">
          <div className="text-[10px] uppercase font-mono text-zinc-500 tracking-wider mb-2">
            Order Reference: {order.orderId}
          </div>
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm font-bold text-zinc-200">{order.bundleSku}</div>
              <div className="text-xs font-mono text-amber-400 font-semibold">
                +{order.coinsToCredit.toLocaleString()} Real Coins
              </div>
            </div>
            <div className="text-xl font-bold font-mono text-zinc-100">
              ${(order.amountMinor / 100).toFixed(2)} USD
            </div>
          </div>
        </div>

        {/* Mock Payment Form */}
        <div className="space-y-4 mb-8">
          <div>
            <label className="block text-[11px] font-mono uppercase text-zinc-400 mb-1">
              Card Number (Simulation)
            </label>
            <div className="relative">
              <input
                type="text"
                readOnly
                value="4242 •••• •••• 4242"
                className="w-full rounded-md border border-white/10 bg-zinc-900 px-3 py-2 text-xs font-mono text-zinc-300"
              />
              <CreditCard className="absolute right-3 top-2.5 h-4 w-4 text-zinc-500" />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[11px] font-mono uppercase text-zinc-400 mb-1">Expiry</label>
              <input
                type="text"
                readOnly
                value="12 / 28"
                className="w-full rounded-md border border-white/10 bg-zinc-900 px-3 py-2 text-xs font-mono text-zinc-300"
              />
            </div>
            <div>
              <label className="block text-[11px] font-mono uppercase text-zinc-400 mb-1">CVC</label>
              <input
                type="text"
                readOnly
                value="•••"
                className="w-full rounded-md border border-white/10 bg-zinc-900 px-3 py-2 text-xs font-mono text-zinc-300"
              />
            </div>
          </div>
        </div>

        {/* Submit */}
        <button
          onClick={handlePay}
          disabled={processing}
          className="w-full flex items-center justify-center gap-2 rounded-lg bg-emerald-500 py-3 text-xs font-bold text-black hover:bg-emerald-400 disabled:opacity-50 transition-colors"
        >
          {processing ? (
            <span>Processing Gateway Cryptographic Callback...</span>
          ) : (
            <>
              <ShieldCheck className="h-4 w-4" />
              <span>Authorize & Pay ${(order.amountMinor / 100).toFixed(2)} USD</span>
            </>
          )}
        </button>

        <p className="mt-4 text-center text-[10px] text-zinc-500 leading-normal">
          Clicking Authorize triggers an authentic signed webhook on the backend. No raw card details are transmitted or stored on Railbound game servers.
        </p>
      </div>
    </div>
  );
}
