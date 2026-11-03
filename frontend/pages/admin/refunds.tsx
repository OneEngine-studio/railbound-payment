import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, ShieldAlert, CheckCircle2, RotateCcw, AlertTriangle, Clock } from "lucide-react";

export default function AdminRefundsPage() {
  const [orderId, setOrderId] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<any | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cases, setCases] = useState<any[]>([]);
  const [loadingCases, setLoadingCases] = useState(true);

  const loadCases = async () => {
    try {
      const data = await api.adminGetRefunds();
      setCases(data);
    } catch {
      // Avoid noisy UI if the finance role is not granted.
    } finally {
      setLoadingCases(false);
    }
  };

  useEffect(() => {
    loadCases();
  }, []);

  const handleRefund = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const refund = await api.adminRequestRefund(orderId.trim(), reason.trim());
      setResult(refund);
      setOrderId("");
      setReason("");
      await loadCases();
    } catch (err: any) {
      setError(err.message || "Refund request failed");
    } finally {
      setSubmitting(false);
    }
  };

  const statusColor = (status: string) => {
    if (status === "COMPLETED") return "text-emerald-400 border-emerald-500/30 bg-emerald-500/10";
    if (status === "REVIEW_REQUIRED") return "text-amber-400 border-amber-500/30 bg-amber-500/10";
    if (status === "FAILED") return "text-rose-400 border-rose-500/30 bg-rose-500/10";
    return "text-sky-300 border-sky-500/30 bg-sky-500/10";
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex items-center gap-3 border-b border-white/[0.08] pb-6 mb-8">
        <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Dispute & Refund Workflows</h1>
          <p className="text-xs text-zinc-400 mt-0.5">
            PayHere Refund API + non-negative Real Coin reversal review.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="space-y-4 text-xs text-zinc-400 leading-relaxed">
          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2 flex items-center gap-2">
              <ShieldAlert className="h-4 w-4 text-amber-400" />
              Non-Negative Balance Rule
            </h2>
            <p>
              A refund never silently reduces a player&apos;s Real Coin balance below zero. If the coins were
              already spent on catalog items, PayHere can still refund the fiat payment, but the case becomes{" "}
              <span className="font-mono text-amber-300">REVIEW_REQUIRED</span> so support can review
              entitlement revocation instead of creating coin debt.
            </p>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2">Double-Entry Audit</h2>
            <p>
              Every successful reversal appends an immutable <span className="font-mono">REFUND</span> ledger
              entry with a negative delta, preserving the invariant: sum of ledger deltas == current wallet
              balance. The provider refund id is stored in <span className="font-mono">refund_cases</span>.
            </p>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2">Requirements</h2>
            <p>
              Set <span className="font-mono">PAYHERE_APP_ID</span> and{" "}
              <span className="font-mono">PAYHERE_APP_SECRET</span> with the &quot;Automated Charging API&quot;
              permission. Refunds are only available after PayHere confirmed the payment (order status{" "}
              <span className="font-mono">COMPLETED</span>).
            </p>
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6">
            <h2 className="text-sm font-bold text-zinc-100 mb-4">Execute Controlled Refund</h2>

            {result && (
              <div className="mb-4 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-xs text-emerald-300">
                <div className="flex items-center gap-2 font-semibold">
                  <CheckCircle2 className="h-4 w-4" />
                  Refund case {result.status}
                </div>
                <p className="mt-1 text-[11px] text-zinc-400">
                  Case {result.refundCaseId} — reversed {result.coinsReversed} / {result.coinsToReverse} RC.
                  {result.status === "REVIEW_REQUIRED" &&
                    " Provider refund succeeded; coin reversal needs support review."}
                </p>
              </div>
            )}

            {error && (
              <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-500/30 bg-rose-500/10 p-4 text-xs text-rose-300">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleRefund} className="space-y-4 text-xs font-mono">
              <div>
                <label className="block text-zinc-400 uppercase text-[10px] mb-1">Target Order ID</label>
                <input
                  type="text"
                  required
                  placeholder="ord_550b1207-98fd-4386..."
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
                disabled={submitting}
                className="w-full flex items-center justify-center gap-2 rounded-md bg-rose-500 py-2.5 text-xs font-semibold text-white hover:bg-rose-400 disabled:opacity-50 transition-colors"
              >
                <RotateCcw className={`h-3.5 w-3.5 ${submitting ? "animate-spin" : ""}`} />
                {submitting ? "Requesting PayHere Refund..." : "Submit Authorized Reversal"}
              </button>
            </form>
          </div>

          <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6">
            <h2 className="text-sm font-bold text-zinc-100 mb-3 flex items-center gap-2">
              <Clock className="h-4 w-4 text-zinc-400" />
              Recent Refund Cases
            </h2>
            {loadingCases ? (
              <p className="text-xs text-zinc-500">Loading cases...</p>
            ) : cases.length === 0 ? (
              <p className="text-xs text-zinc-500">No refund cases recorded.</p>
            ) : (
              <ul className="space-y-2 text-[11px] font-mono">
                {cases.slice(0, 8).map((c) => (
                  <li key={c.refundCaseId} className="rounded border border-white/5 bg-zinc-950 p-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-zinc-300 break-all">{c.orderId}</span>
                      <span className={`rounded border px-1.5 py-0.5 text-[9px] font-bold ${statusColor(c.status)}`}>
                        {c.status}
                      </span>
                    </div>
                    <div className="mt-1 text-zinc-500">
                      {c.coinsReversed}/{c.coinsToReverse} RC · {c.providerRefundId || "no refund id"}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
