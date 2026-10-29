import React from "react";
import Link from "next/link";
import { useAuth } from "../lib/auth";
import { User, Shield, Coins, ShoppingBag, Terminal, Key } from "lucide-react";

export default function ProfilePage() {
  const { user, wallet, isAdmin, loginAs } = useAuth();

  if (!user) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center text-xs font-mono text-zinc-500">
        Please sign in to view operative profile.
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <div className="border-b border-white/[0.08] pb-6 mb-8">
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Operative Profile & Identity</h1>
        <p className="text-xs text-zinc-400 mt-1">
          Synchronized Railbound Battle Royale credentials and authoritative session status.
        </p>
      </div>

      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 sm:p-8 space-y-6">
        {/* Top Profile Bar */}
        <div className="flex items-center gap-4 pb-6 border-b border-white/5">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-zinc-800 text-zinc-300 font-bold text-xl">
            {user.displayName.charAt(0)}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-zinc-100">{user.displayName}</h2>
              <span className="rounded bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 text-[10px] font-mono text-amber-400 font-bold">
                {user.role}
              </span>
            </div>
            <div className="text-xs font-mono text-zinc-500 mt-0.5">
              Portal User ID: {user.userId}
            </div>
          </div>
        </div>

        {/* Identity & Technical Specifications */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-mono">
          <div className="rounded-xl border border-white/5 bg-zinc-950 p-4 space-y-1">
            <span className="text-[10px] uppercase text-zinc-500">Nakama Canonical UUID</span>
            <div className="text-zinc-200 break-all">{user.nakamaUserId}</div>
          </div>

          <div className="rounded-xl border border-white/5 bg-zinc-950 p-4 space-y-1">
            <span className="text-[10px] uppercase text-zinc-500">Authoritative Real Coins</span>
            <div className="text-amber-400 font-bold text-base">
              {wallet?.realCoins.toLocaleString() ?? 0} RC
            </div>
          </div>
        </div>

        {/* Quick Links */}
        <div className="pt-4 flex flex-wrap gap-3">
          <Link
            href="/wallet"
            className="rounded-md border border-white/10 bg-zinc-900 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-800 transition-colors"
          >
            Inspect Ledger
          </Link>
          <Link
            href="/inventory"
            className="rounded-md border border-white/10 bg-zinc-900 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-800 transition-colors"
          >
            View Armory Loadout
          </Link>
          <Link
            href="/purchase-history"
            className="rounded-md border border-white/10 bg-zinc-900 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-800 transition-colors"
          >
            Purchase History
          </Link>
          {isAdmin && (
            <Link
              href="/admin"
              className="rounded-md bg-amber-500/10 border border-amber-500/30 px-4 py-2 text-xs font-semibold text-amber-400 hover:bg-amber-500/20 transition-colors"
            >
              Open Admin Console
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
