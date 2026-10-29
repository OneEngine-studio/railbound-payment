import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, Users, RefreshCw } from "lucide-react";

export default function AdminPlayersPage() {
  const [players, setPlayers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const loadPlayers = async () => {
    try {
      setLoading(true);
      const data = await api.adminGetPlayers();
      setPlayers(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPlayers();
  }, []);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Synchronized Player Directory</h1>
            <p className="text-xs text-zinc-400 mt-0.5">
              Player identities mapped authoritatively from Nakama game servers.
            </p>
          </div>
        </div>

        <button
          onClick={loadPlayers}
          className="inline-flex items-center gap-1.5 rounded-lg border border-white/10 bg-zinc-900 px-3 py-1.5 text-xs text-zinc-300 hover:bg-zinc-800 transition-colors"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Refresh Directory
        </button>
      </div>

      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-6 py-3">Display Name</th>
                <th className="px-6 py-3">Portal User ID</th>
                <th className="px-6 py-3">Nakama Canonical UUID</th>
                <th className="px-6 py-3">Role</th>
                <th className="px-6 py-3 text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-zinc-500">
                    Loading accounts...
                  </td>
                </tr>
              ) : players.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-zinc-500">
                    No players found.
                  </td>
                </tr>
              ) : (
                players.map((p) => (
                  <tr key={p.userId} className="hover:bg-white/[0.02]">
                    <td className="px-6 py-3 font-semibold text-zinc-100">{p.displayName}</td>
                    <td className="px-6 py-3 text-zinc-400">{p.userId}</td>
                    <td className="px-6 py-3 text-zinc-500 break-all">{p.nakamaUserId}</td>
                    <td className="px-6 py-3">
                      <span className="rounded bg-zinc-800 px-2 py-0.5 text-[9px] font-bold text-amber-400">
                        {p.role}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-right">
                      <span className="rounded bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 text-[9px] text-emerald-400 uppercase font-bold">
                        {p.status}
                      </span>
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
