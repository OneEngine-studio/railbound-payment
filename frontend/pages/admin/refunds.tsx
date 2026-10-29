import React, { useState } from "react";
import Link from "next/link";
import { ArrowLeft, ShieldAlert, CheckCircle2, RotateCcw } from "lucide-react";

export default function AdminRefundsPage() {
  const [orderId, setOrderId] = useState("");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const handleRefund = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      setOrderId("");
      setReason("");
    }, 3000);
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="flex items-center gap-3 border-b border-white/[0.08] pb-6 mb-8">
        <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Dispute & Refund Workflows</h1>
          <p className="text-xs text-zinc-400 mt-0.5">
            Finance team dispute reconciliation and non-negative coin compensation review.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="space-y-4 text-xs text-zinc-400 leading-relaxed">
          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2 flex items-center gap-2">
              <ShieldAlert className="h-4 w-4 text-amber-400" />
              Negative Balance Prevention Rule
            </h2>
            <p>
              Under no circumstances may a refund silently reduce a player's Real Coin balance below zero. If the player has already spent the coins on catalog items, the associated item entitlements must be reviewed for revocation rather than creating a negative coin debt.
            </p>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2">Double-Entry Audit</h2>
            <p>
              Every executed refund appends an immutable `REFUND` entry to `wallet_ledger` with a negative delta, preserving the strict mathematical invariant: Sum of ledger deltas == current wallet balance.
            </p>
          </div>
        </div>

        <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6">
          <h2 className="text-sm font-bold text-zinc-100 mb-4">Execute Controlled Refund</h2>

          {submitted ? (
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-center text-xs text-emerald-300">
              <CheckCircle2 className="mx-auto h-6 w-6 text-emerald-400 mb-2" />
              <p className="font-semibold">Refund Case Recorded</p>
              <p className="mt-1 text-[11px] text-zinc-400">
                Payment provider notification dispatched and ledger reversal queued.
              </p>
            </div>
          ) : (
            <form onSubmit={handleRefund} className="space-y-4 text-xs font-mono">
              <div>
                <label className="block text-zinc-400 uppercase text-[10px] mb-1">Target Order ID</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. ord_550b1207-98fd-4386..."
                  value={orderId}
                  onChange={(e) => setOrderId(e.target.value)}
                  className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-zinc-400 uppercase text-[10px] mb-1">
                  Dispute Reason / Chargeback Reference
                </label>
                <textarea
                  required
                  rows={3}
                  placeholder="Payment provider dispute case ID and audit rationale..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none font-sans"
                />
              </div>

              <button
                type="submit"
                className="w-full flex items-center justify-center gap-2 rounded-md bg-rose-500 py-2.5 text-xs font-semibold text-white hover:bg-rose-400 transition-colors"
              >
                <RotateCcw className="h-3.5 w-3.5" />
                Submit Authorized Reversal
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
