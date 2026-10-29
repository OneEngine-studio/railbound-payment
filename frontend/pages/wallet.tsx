import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { Coins, Plus, ShieldCheck, ArrowDownLeft, ArrowUpRight, History } from "lucide-react";

export default function WalletPage() {
  const { wallet, refreshWallet, user } = useAuth();
  const [ledger, setLedger] = useState<any[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);

  const loadLedger = async (targetPage: number) => {
    try {
      setLoading(true);
      const res = await api.getLedger(targetPage, 15);
      setLedger(res.items || []);
      setPage(res.page || 0);
      setTotalPages(res.totalPages || 1);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshWallet();
    loadLedger(page);
  }, [page]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Wallet & Double-Entry Ledger</h1>
          <p className="text-xs text-zinc-400 mt-1">
            Authoritative financial balances and immutable append-only transaction history.
          </p>
        </div>

        <Link
          href="/top-up"
          className="inline-flex items-center gap-2 rounded-lg bg-amber-500 px-4 py-2 text-xs font-semibold text-black hover:bg-amber-400 transition-colors self-start sm:self-auto"
        >
          <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
          Top-Up Real Coins
        </Link>
      </div>

      {/* Balance Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-10">
        {/* Real Coins (Active Authoritative System) */}
        <div className="rounded-2xl border border-amber-500/30 bg-gradient-to-br from-amber-500/[0.08] to-transparent p-6 relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="rounded bg-amber-500/20 px-2 py-0.5 text-[10px] font-mono text-amber-300 font-semibold uppercase">
              Authoritative Currency
            </span>
            <Coins className="h-5 w-5 text-amber-400" />
          </div>

          <div className="mt-6">
            <span className="text-xs font-mono text-zinc-400 uppercase tracking-wider">Real Coin Balance</span>
            <div className="mt-1 flex items-baseline gap-2">
              <span className="text-4xl font-extrabold font-mono text-amber-400">
                {wallet ? wallet.realCoins.toLocaleString() : "0"}
              </span>
              <span className="text-sm font-mono text-amber-400/70">RC</span>
            </div>
          </div>

          <p className="mt-4 text-[11px] text-zinc-500 border-t border-white/5 pt-3">
            Authoritative system of record: MongoDB replica set (rs0). Guaranteed single-credit top-up and double-entry reconciliation.
          </p>
        </div>

        {/* Game Coins (Soft Progression Currency - Deferred) */}
        <div className="rounded-2xl border border-white/5 bg-zinc-950 p-6 opacity-60">
          <div className="flex items-center justify-between">
            <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-500 uppercase">
              Deferred Roadmap Scope
            </span>
          </div>

          <div className="mt-6">
            <span className="text-xs font-mono text-zinc-500 uppercase tracking-wider">Game Coins (Soft Currency)</span>
            <div className="mt-1 flex items-baseline gap-2">
              <span className="text-4xl font-extrabold font-mono text-zinc-600">--</span>
              <span className="text-sm font-mono text-zinc-600">GC</span>
            </div>
          </div>

          <p className="mt-4 text-[11px] text-zinc-600 border-t border-white/5 pt-3">
            Earnable in-game progression currency. Spending and acquisition deferred per currency policy; schema reserved.
          </p>
        </div>
      </div>

      {/* Transaction Ledger Table */}
      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/5 bg-zinc-950/40">
          <div className="flex items-center gap-2">
            <History className="h-4 w-4 text-zinc-400" />
            <h2 className="text-sm font-semibold text-zinc-200">Immutable Transaction Ledger</h2>
          </div>
          <span className="text-[10px] font-mono text-zinc-500">Append-Only / Double-Entry</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-6 py-3">Timestamp</th>
                <th className="px-6 py-3">Entry ID</th>
                <th className="px-6 py-3">Type</th>
                <th className="px-6 py-3">Description</th>
                <th className="px-6 py-3 text-right">Delta</th>
                <th className="px-6 py-3 text-right">Balance After</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {loading ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-zinc-500">
                    Loading ledger records...
                  </td>
                </tr>
              ) : ledger.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-zinc-500">
                    No transactions recorded yet. Complete a top-up or shop purchase to generate entries.
                  </td>
                </tr>
              ) : (
                ledger.map((entry) => {
                  const isPositive = entry.delta > 0;
                  return (
                    <tr key={entry.entryId} className="hover:bg-white/[0.02] transition-colors">
                      <td className="px-6 py-3 text-zinc-500 whitespace-nowrap">
                        {new Date(entry.createdAt).toLocaleString()}
                      </td>
                      <td className="px-6 py-3 text-zinc-400 font-mono">{entry.entryId.substring(0, 16)}...</td>
                      <td className="px-6 py-3">
                        <span
                          className={`rounded px-2 py-0.5 text-[9px] uppercase font-bold ${
                            entry.type === "TOPUP"
                              ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                              : entry.type === "PURCHASE"
                              ? "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                              : "bg-zinc-800 text-zinc-300"
                          }`}
                        >
                          {entry.type}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-zinc-300 max-w-xs truncate">{entry.description}</td>
                      <td
                        className={`px-6 py-3 text-right font-bold ${
                          isPositive ? "text-emerald-400" : "text-rose-400"
                        }`}
                      >
                        {isPositive ? `+${entry.delta.toLocaleString()}` : entry.delta.toLocaleString()} RC
                      </td>
                      <td className="px-6 py-3 text-right text-zinc-200 font-bold">
                        {entry.balanceAfter.toLocaleString()} RC
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-3 border-t border-white/5 text-xs text-zinc-400">
            <span>
              Page {page + 1} of {totalPages}
            </span>
            <div className="flex gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded border border-white/10 px-3 py-1 disabled:opacity-40"
              >
                Previous
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="rounded border border-white/10 px-3 py-1 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
