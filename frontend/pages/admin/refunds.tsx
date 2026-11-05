import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, ShieldAlert, RotateCcw, RefreshCw } from "lucide-react";

const STATUS_STYLE: Record<string, string> = {
  COINS_REVERSED: "bg-amber-500/10 text-amber-400 border-amber-500/30",
  NEEDS_REVIEW: "bg-rose-500/10 text-rose-400 border-rose-500/30",
  COMPLETED: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
};

export default function AdminRefundsPage() {
  const [cases, setCases] = useState<any[]>([]);
  const [review, setReview] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [orderId, setOrderId] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [c, r] = await Promise.all([api.adminGetRefunds(), api.adminGetOrdersInReview()]);
      setCases(c);
      setReview(r);
      setError(null);
    } catch (err: any) {
      setError(err.message || "Failed to load refund data (finance role required)");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: () => Promise<any>, okMessage: string) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(okMessage);
      await load();
    } catch (err: any) {
      setError(err.message || "Action failed");
    } finally {
      setBusy(false);
    }
  };

  const handleRefund = (e: React.FormEvent) => {
    e.preventDefault();
    run(async () => {
      await api.adminCreateRefund(orderId.trim(), reason.trim());
      setOrderId("");
      setReason("");
    }, "Refund case created.");
  };

  const confirmRefund = (refundId: string) => {
    const ref = window.prompt("PayHere refund reference (optional). Only confirm after refunding in the PayHere dashboard:");
    if (ref === null) return;
    run(() => api.adminConfirmRefund(refundId, ref), "Refund confirmed.");
  };

  const resolveReview = (order: any, approve: boolean) => {
    const note = window.prompt(
      approve
        ? "Approve: confirm in the PayHere dashboard that the full amount was paid. Enter a note:"
        : "Reject: enter the reason:"
    );
    if (!note || !note.trim()) return;
    run(() => api.adminResolveReview(order.orderId, approve, note.trim()), approve ? "Payment approved and coins credited." : "Payment rejected.");
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex items-center gap-3 border-b border-white/[0.08] pb-6 mb-8">
        <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div className="flex-1">
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Disputes, Refunds & Payment Review</h1>
          <p className="text-xs text-zinc-400 mt-0.5">
            Coin reversal never takes a balance below zero. Money refunds are made in the PayHere dashboard and confirmed here.
          </p>
        </div>
        <button onClick={load} className="text-zinc-400 hover:text-white" title="Refresh">
          <RefreshCw className="h-4 w-4" />
        </button>
      </div>

      {error && <div className="mb-4 rounded-md border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">{error}</div>}
      {notice && <div className="mb-4 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300">{notice}</div>}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-10">
        <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5 text-xs text-zinc-400 leading-relaxed">
          <h2 className="text-sm font-bold text-zinc-100 mb-2 flex items-center gap-2">
            <ShieldAlert className="h-4 w-4 text-amber-400" />
            How reversals work
          </h2>
          <p className="mb-2">
            Creating a refund removes the order&apos;s coins from the player&apos;s wallet and writes a negative <code>REFUND</code> ledger entry. If the player has already spent the coins, the case is parked as <strong>NEEDS_REVIEW</strong> instead of creating a negative balance. Review the player&apos;s purchases, then retry.
          </p>
          <p>
            After the coins are reversed, refund the customer in the PayHere dashboard and press <strong>Confirm refunded</strong>. Chargebacks reported by PayHere open a case automatically.
          </p>
        </div>

        <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6">
          <h2 className="text-sm font-bold text-zinc-100 mb-4">Open a refund</h2>
          <form onSubmit={handleRefund} className="space-y-4 text-xs font-mono">
            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Order ID (COMPLETED orders only)</label>
              <input
                type="text"
                required
                placeholder="ord_..."
                value={orderId}
                onChange={(e) => setOrderId(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Reason</label>
              <textarea
                required
                rows={3}
                placeholder="Customer request / dispute reference..."
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none font-sans"
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className="w-full flex items-center justify-center gap-2 rounded-md bg-rose-500 py-2.5 text-xs font-semibold text-white hover:bg-rose-400 disabled:opacity-50 transition-colors"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              Create refund case
            </button>
          </form>
        </div>
      </div>

      <h2 className="text-sm font-bold text-zinc-100 mb-3">Payments awaiting review ({review.length})</h2>
      <div className="mb-10 rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        {review.length === 0 ? (
          <div className="p-5 text-xs text-zinc-500 font-mono">No payments are waiting for review.</div>
        ) : (
          <table className="w-full text-left text-xs">
            <tbody className="divide-y divide-white/5">
              {review.map((o) => (
                <tr key={o.orderId}>
                  <td className="px-4 py-3 font-mono text-zinc-300 break-all">{o.orderId}</td>
                  <td className="px-4 py-3 text-zinc-400">{o.statusMessage}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button disabled={busy} onClick={() => resolveReview(o, true)} className="mr-2 rounded bg-emerald-600 px-2 py-1 text-[11px] text-white hover:bg-emerald-500 disabled:opacity-50">Approve</button>
                    <button disabled={busy} onClick={() => resolveReview(o, false)} className="rounded bg-zinc-700 px-2 py-1 text-[11px] text-white hover:bg-zinc-600 disabled:opacity-50">Reject</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <h2 className="text-sm font-bold text-zinc-100 mb-3">Refund & chargeback cases ({cases.length})</h2>
      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-x-auto">
        {loading ? (
          <div className="p-5 text-xs text-zinc-500 font-mono">Loading...</div>
        ) : cases.length === 0 ? (
          <div className="p-5 text-xs text-zinc-500 font-mono">No refund cases yet.</div>
        ) : (
          <table className="w-full text-left text-xs">
            <thead className="bg-zinc-950/50 text-[10px] uppercase text-zinc-500 font-mono">
              <tr>
                <th className="px-4 py-3">Case</th>
                <th className="px-4 py-3">Order</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3 text-right">Coins</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {cases.map((c) => (
                <tr key={c.refundId} className="align-top">
                  <td className="px-4 py-3 font-mono text-zinc-400 break-all">{c.refundId}</td>
                  <td className="px-4 py-3 font-mono text-zinc-300 break-all">{c.orderId}</td>
                  <td className="px-4 py-3 text-zinc-300">{c.type}</td>
                  <td className="px-4 py-3 text-right font-mono text-zinc-300">
                    {c.coinsReversed.toLocaleString()} / {c.coinsToReverse.toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`rounded border px-2 py-0.5 text-[9px] font-bold uppercase ${STATUS_STYLE[c.status] || ""}`}>{c.status}</span>
                    {c.resolutionNote && <div className="mt-1 max-w-xs text-[11px] text-zinc-500">{c.resolutionNote}</div>}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {c.status === "NEEDS_REVIEW" && (
                      <button disabled={busy} onClick={() => run(() => api.adminRetryRefund(c.refundId), "Reversal retried.")} className="rounded bg-amber-500 px-2 py-1 text-[11px] font-semibold text-black hover:bg-amber-400 disabled:opacity-50">Retry reversal</button>
                    )}
                    {c.status === "COINS_REVERSED" && c.type === "REFUND" && (
                      <button disabled={busy} onClick={() => confirmRefund(c.refundId)} className="rounded bg-emerald-600 px-2 py-1 text-[11px] text-white hover:bg-emerald-500 disabled:opacity-50">Confirm refunded</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
