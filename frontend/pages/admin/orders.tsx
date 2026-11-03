import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, RefreshCw, SearchCheck, AlertTriangle } from "lucide-react";

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [reconciling, setReconciling] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadOrders = async () => {
    try {
      setLoading(true);
      const data = await api.adminGetOrders();
      setOrders(data);
    } catch (err: any) {
      setError(err.message || "Failed to load orders");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
  }, []);

  const handleReconcile = async (orderId: string) => {
    setReconciling(orderId);
    setError(null);
    setMessage(null);
    try {
      const result = await api.adminReconcileOrder(orderId);
      setMessage(
        `Order ${orderId}: PayHere reports ${result.providerStatus}. ` +
          `Status ${result.previousStatus} → ${result.currentStatus}.`
      );
      await loadOrders();
    } catch (err: any) {
      setError(err.message || "Reconciliation failed");
    } finally {
      setReconciling(null);
    }
  };

  const statusClass = (status: string) => {
    if (status === "COMPLETED") return "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30";
    if (status === "PENDING" || status === "PROCESSING")
      return "bg-amber-500/10 text-amber-400 border border-amber-500/30";
    if (status === "REFUND_PENDING")
      return "bg-sky-500/10 text-sky-300 border border-sky-500/30";
    return "bg-rose-500/10 text-rose-400 border border-rose-500/30";
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Payment Orders Oversight</h1>
            <p className="text-xs text-zinc-400 mt-0.5">
              Live tracking of PayHere top-up orders and cryptographically verified fulfillment status.
            </p>
          </div>
        </div>

        <button
          onClick={loadOrders}
          className="inline-flex items-center gap-1.5 rounded-lg border border-white/10 bg-zinc-900 px-3 py-1.5 text-xs text-zinc-300 hover:bg-zinc-800 transition-colors"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Refresh Orders
        </button>
      </div>

      {message && (
        <div className="mb-4 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-300">
          {message}
        </div>
      )}
      {error && (
        <div className="mb-4 flex items-center gap-2 rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-300">
          <AlertTriangle className="h-3.5 w-3.5" />
          {error}
        </div>
      )}

      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-5 py-3">Timestamp</th>
                <th className="px-5 py-3">Order ID</th>
                <th className="px-5 py-3">Provider</th>
                <th className="px-5 py-3">User ID</th>
                <th className="px-5 py-3">Bundle</th>
                <th className="px-5 py-3 text-right">Coins</th>
                <th className="px-5 py-3 text-right">Amount</th>
                <th className="px-5 py-3 text-right">Status</th>
                <th className="px-5 py-3 text-right">Reconcile</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {loading ? (
                <tr>
                  <td colSpan={9} className="px-6 py-8 text-center text-zinc-500">
                    Loading orders...
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={9} className="px-6 py-8 text-center text-zinc-500">
                    No orders registered in system.
                  </td>
                </tr>
              ) : (
                orders.map((o) => (
                  <tr key={o.orderId} className="hover:bg-white/[0.02]">
                    <td className="px-5 py-3 text-zinc-500 whitespace-nowrap">
                      {new Date(o.createdAt).toLocaleString()}
                    </td>
                    <td className="px-5 py-3 font-semibold text-zinc-200">
                      {String(o.orderId).substring(0, 14)}...
                    </td>
                    <td className="px-5 py-3 text-zinc-400 uppercase">{o.provider || "—"}</td>
                    <td className="px-5 py-3 text-zinc-400">{o.userId}</td>
                    <td className="px-5 py-3 text-zinc-300">{o.bundleSku}</td>
                    <td className="px-5 py-3 text-right font-bold text-amber-400">
                      +{Number(o.coinsToCredit).toLocaleString()}
                    </td>
                    <td className="px-5 py-3 text-right font-mono text-zinc-200">
                      {(o.amountMinor / 100).toFixed(2)} {o.currency}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <span className={`rounded px-2 py-0.5 text-[9px] uppercase font-bold ${statusClass(o.status)}`}>
                        {o.status}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-right">
                      {o.provider === "payhere" ? (
                        <button
                          onClick={() => handleReconcile(o.orderId)}
                          disabled={reconciling === o.orderId}
                          title="Ask PayHere's Retrieval API for the authoritative payment status"
                          className="inline-flex items-center gap-1 rounded border border-white/10 px-2 py-1 text-[10px] text-zinc-300 hover:bg-zinc-800 disabled:opacity-40"
                        >
                          <SearchCheck className="h-3 w-3" />
                          {reconciling === o.orderId ? "Checking..." : "Check"}
                        </button>
                      ) : (
                        <span className="text-zinc-600">—</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
