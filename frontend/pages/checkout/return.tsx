import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";
import Link from "next/link";
import { api } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import { CheckCircle2, Clock, AlertTriangle, Coins, Ban } from "lucide-react";

const MAX_POLLS = 200; // ~5 minutes at 1.5s intervals

export default function CheckoutReturn() {
  const router = useRouter();
  const { orderId, cancelled } = router.query;
  const { refreshWallet, wallet } = useAuth();

  const [order, setOrder] = useState<any | null>(null);
  const [status, setStatus] = useState<"PROCESSING" | "COMPLETED" | "FAILED" | "CANCELLED">("PROCESSING");
  const [pollCount, setPollCount] = useState(0);

  useEffect(() => {
    if (!router.isReady) return;
    if (cancelled === "1") {
      setStatus("CANCELLED");
      return;
    }
    if (!orderId) return;

    let isMounted = true;
    let done = false;

    const check = async () => {
      if (done) return;
      try {
        const data = await api.getOrder(orderId as string);
        if (!isMounted) return;
        setOrder(data);

        if (data.status === "COMPLETED") {
          done = true;
          setStatus("COMPLETED");
          await refreshWallet();
        } else if (data.status === "FAILED" || data.status === "CANCELLED" || data.status === "EXPIRED") {
          done = true;
          setStatus(data.status === "CANCELLED" ? "CANCELLED" : "FAILED");
        } else if (data.status === "REFUND_PENDING" || data.status === "REFUNDED") {
          done = true;
          setStatus("FAILED");
        }
      } catch (err) {
        console.warn("Polling order status error:", err);
      }
    };

    check();
    const interval = setInterval(() => {
      if (done) {
        clearInterval(interval);
        return;
      }
      setPollCount((prev) => Math.min(prev + 1, MAX_POLLS));
      check();
    }, 1500);

    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [orderId, cancelled, router.isReady]);

  return (
    <div className="mx-auto max-w-md px-4 py-20 sm:px-6">
      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-8 text-center shadow-xl">
        {status === "PROCESSING" && (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-500/10 text-amber-400 mb-4 animate-pulse">
              <Clock className="h-7 w-7" />
            </div>
            <h1 className="text-lg font-bold text-zinc-100">
              {pollCount >= MAX_POLLS ? "Payment Still Processing" : "Verifying Payment Callback..."}
            </h1>
            <p className="mt-2 text-xs text-zinc-400 leading-relaxed">
              {pollCount >= MAX_POLLS
                ? "PayHere has not confirmed the payment yet. Coins are only credited after a verified server callback. You can safely close this page and check your wallet later."
                : "Awaiting the authoritative server-to-server confirmation from PayHere. Closing the browser does not cancel a successful payment."}
            </p>
            <div className="mt-6 rounded-md bg-zinc-950 p-3 text-[11px] font-mono text-zinc-500 break-all">
              Order: {orderId || "Resolving..."} (Poll cycle: {pollCount})
            </div>
            {pollCount >= MAX_POLLS && (
              <Link
                href="/wallet"
                className="mt-6 inline-flex rounded-lg bg-zinc-800 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-700"
              >
                Check Wallet
              </Link>
            )}
          </div>
        )}

        {status === "COMPLETED" && (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-400 mb-4">
              <CheckCircle2 className="h-8 w-8" />
            </div>

            <h1 className="text-xl font-bold text-zinc-100">Payment Verified & Coins Credited!</h1>
            <p className="mt-2 text-xs text-zinc-400">
              The PayHere callback was cryptographically verified and atomically fulfilled exactly once.
            </p>

            {order && (
              <div className="mt-6 rounded-xl border border-white/5 bg-zinc-950 p-4 space-y-2 text-xs font-mono">
                <div className="flex justify-between text-zinc-400">
                  <span>Coins Credited:</span>
                  <span className="font-bold text-amber-400">+{order.coinsToCredit.toLocaleString()} RC</span>
                </div>
                <div className="flex justify-between text-zinc-400">
                  <span>Amount Paid:</span>
                  <span className="text-zinc-200">
                    {(order.amountMinor / 100).toFixed(2)} {order.currency}
                  </span>
                </div>
                <div className="flex justify-between text-zinc-400">
                  <span>Gateway:</span>
                  <span className="text-zinc-200 uppercase">{order.provider}</span>
                </div>
                {order.providerPaymentId && (
                  <div className="flex justify-between text-zinc-400">
                    <span>Payment Ref:</span>
                    <span className="text-zinc-200 break-all">{order.providerPaymentId}</span>
                  </div>
                )}
                <div className="flex justify-between text-zinc-400">
                  <span>New Authoritative Balance:</span>
                  <span className="font-bold text-emerald-400">{wallet?.realCoins.toLocaleString()} RC</span>
                </div>
              </div>
            )}

            <div className="mt-8 flex flex-col sm:flex-row items-center gap-3">
              <Link
                href="/store"
                className="w-full rounded-lg bg-amber-500 py-2.5 text-xs font-semibold text-black hover:bg-amber-400 transition-colors"
              >
                Go to Armory Store
              </Link>
              <Link
                href="/wallet"
                className="w-full rounded-lg border border-white/10 bg-zinc-900 py-2.5 text-xs font-medium text-zinc-200 hover:bg-zinc-800 transition-colors"
              >
                Inspect Ledger
              </Link>
            </div>
          </div>
        )}

        {status === "CANCELLED" && (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-zinc-500/10 text-zinc-400 mb-4">
              <Ban className="h-8 w-8" />
            </div>
            <h1 className="text-lg font-bold text-zinc-100">Checkout Cancelled</h1>
            <p className="mt-2 text-xs text-zinc-400">
              No payment was taken. Your Real Coin balance is unchanged.
            </p>
            <div className="mt-6">
              <Link
                href="/top-up"
                className="inline-flex items-center gap-1.5 rounded-lg bg-zinc-800 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-700"
              >
                Try Again
              </Link>
            </div>
          </div>
        )}

        {status === "FAILED" && (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-500/10 text-rose-400 mb-4">
              <AlertTriangle className="h-8 w-8" />
            </div>
            <h1 className="text-lg font-bold text-rose-200">Payment Fulfillment Failed</h1>
            <p className="mt-2 text-xs text-zinc-400">
              The payment was declined, expired, cancelled, or the callback was rejected. No coins were
              credited. If your card was charged, contact support with the order reference.
            </p>
            <div className="mt-6 flex items-center justify-center gap-3">
              <Link
                href="/top-up"
                className="inline-flex items-center gap-1.5 rounded-lg bg-zinc-800 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-700"
              >
                Try Again
              </Link>
              <Link
                href="/support"
                className="inline-flex items-center gap-1.5 rounded-lg border border-white/10 px-4 py-2 text-xs text-zinc-300 hover:bg-zinc-800"
              >
                Contact Support
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
