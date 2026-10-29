import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import {
  Users,
  CreditCard,
  Coins,
  Package,
  ArrowRight,
  TrendingUp,
  AlertTriangle,
  Terminal,
  ShieldCheck,
  Plus
} from "lucide-react";

export default function AdminDashboard() {
  const { user, isAdmin } = useAuth();
  const [summary, setSummary] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.adminGetSummary()
      .then(setSummary)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Admin Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div>
          <div className="flex items-center gap-2 text-xs font-mono text-amber-400 mb-1">
            <Terminal className="h-3.5 w-3.5" />
            <span>QUARTERMASTER COMMAND // ADMIN OVERSIGHT</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Portal Control Center</h1>
        </div>

        <div className="flex items-center gap-2">
          <Link
            href="/admin/items/new"
            className="inline-flex items-center gap-1.5 rounded-lg bg-amber-500 px-3.5 py-2 text-xs font-semibold text-black hover:bg-amber-400 transition-colors"
          >
            <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
            New Item Draft
          </Link>
          <Link
            href="/admin/news"
            className="inline-flex items-center gap-1.5 rounded-lg border border-white/10 bg-zinc-900 px-3.5 py-2 text-xs font-medium text-zinc-200 hover:bg-zinc-800 transition-colors"
          >
            Publish News
          </Link>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-10">
        <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
          <div className="flex items-center justify-between text-zinc-500 mb-3">
            <span className="text-xs font-mono uppercase">Gross Fiat Revenue</span>
            <CreditCard className="h-4 w-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-zinc-100">
            ${summary ? summary.totalRevenueUsd.toFixed(2) : "0.00"}
          </div>
          <span className="text-[10px] font-mono text-zinc-500">From completed top-ups</span>
        </div>

        <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
          <div className="flex items-center justify-between text-zinc-500 mb-3">
            <span className="text-xs font-mono uppercase">Real Coins In Circulation</span>
            <Coins className="h-4 w-4 text-amber-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-amber-400">
            {summary ? summary.totalCoinsCirculation.toLocaleString() : "0"}
          </div>
          <span className="text-[10px] font-mono text-zinc-500">Authoritative wallet balances</span>
        </div>

        <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
          <div className="flex items-center justify-between text-zinc-500 mb-3">
            <span className="text-xs font-mono uppercase">Top-Up Orders</span>
            <TrendingUp className="h-4 w-4 text-zinc-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-zinc-100">
            {summary ? summary.completedOrders : 0} / {summary ? summary.totalOrders : 0}
          </div>
          <span className="text-[10px] font-mono text-emerald-400">Fulfilled orders</span>
        </div>

        <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
          <div className="flex items-center justify-between text-zinc-500 mb-3">
            <span className="text-xs font-mono uppercase">Catalog Assets</span>
            <Package className="h-4 w-4 text-zinc-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-zinc-100">
            {summary ? summary.totalCatalogItems : 0}
          </div>
          <span className="text-[10px] font-mono text-zinc-500">Published & draft items</span>
        </div>
      </div>

      {/* Admin Modules Navigation */}
      <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wider mb-4 font-mono">
        Management Modules
      </h2>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-10">
        <Link
          href="/admin/items"
          className="group rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-amber-500/50 transition-all"
        >
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-bold text-zinc-100 group-hover:text-amber-400 transition-colors">
              Item Publishing & Releases
            </h3>
            <ArrowRight className="h-4 w-4 text-zinc-500 group-hover:text-amber-400 transition-colors" />
          </div>
          <p className="text-xs text-zinc-400">
            Author draft items, validate client version compatibility, and publish live to store.
          </p>
        </Link>

        <Link
          href="/admin/orders"
          className="group rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-amber-500/50 transition-all"
        >
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-bold text-zinc-100 group-hover:text-amber-400 transition-colors">
              Payment Orders Oversight
            </h3>
            <ArrowRight className="h-4 w-4 text-zinc-500 group-hover:text-amber-400 transition-colors" />
          </div>
          <p className="text-xs text-zinc-400">
            Inspect top-up orders, verify gateway session tokens, and trace fulfillment logs.
          </p>
        </Link>

        <Link
          href="/admin/news"
          className="group rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-amber-500/50 transition-all"
        >
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-bold text-zinc-100 group-hover:text-amber-400 transition-colors">
              News & In-Game Broadcasts
            </h3>
            <ArrowRight className="h-4 w-4 text-zinc-500 group-hover:text-amber-400 transition-colors" />
          </div>
          <p className="text-xs text-zinc-400">
            Compose announcements and patch briefings synchronized to Unity client lobby feeds.
          </p>
        </Link>

        <Link
          href="/admin/players"
          className="group rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-amber-500/50 transition-all"
        >
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-bold text-zinc-100 group-hover:text-amber-400 transition-colors">
              Player Account Directory
            </h3>
            <ArrowRight className="h-4 w-4 text-zinc-500 group-hover:text-amber-400 transition-colors" />
          </div>
          <p className="text-xs text-zinc-400">
            Lookup synchronized Nakama player identities and authoritative wallet balances.
          </p>
        </Link>

        <Link
          href="/admin/refunds"
          className="group rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-amber-500/50 transition-all"
        >
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-bold text-zinc-100 group-hover:text-amber-400 transition-colors">
              Disputes & Reversals
            </h3>
            <ArrowRight className="h-4 w-4 text-zinc-500 group-hover:text-amber-400 transition-colors" />
          </div>
          <p className="text-xs text-zinc-400">
            Controlled refund workflows and non-negative coin compensation review.
          </p>
        </Link>

        <Link
          href="/admin/audit"
          className="group rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-amber-500/50 transition-all"
        >
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-bold text-zinc-100 group-hover:text-amber-400 transition-colors">
              Audit Logs & Invariants
            </h3>
            <ArrowRight className="h-4 w-4 text-zinc-500 group-hover:text-amber-400 transition-colors" />
          </div>
          <p className="text-xs text-zinc-400">
            Immutable administrative audit trails and hourly ledger reconciliation health.
          </p>
        </Link>
      </div>
    </div>
  );
}
