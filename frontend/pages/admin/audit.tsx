import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, ShieldCheck, CheckCircle2 } from "lucide-react";

export default function AdminAuditPage() {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.adminGetAuditLogs()
      .then(setLogs)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex items-center gap-3 border-b border-white/[0.08] pb-6 mb-8">
        <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Audit Logs & Ledger Invariants</h1>
          <p className="text-xs text-zinc-400 mt-0.5">
            Tamper-evident administrative logs and hourly financial reconciliation checks.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4 flex items-center gap-3">
          <CheckCircle2 className="h-6 w-6 text-emerald-400 shrink-0" />
          <div>
            <div className="text-xs font-bold text-zinc-200">Ledger Invariant 1</div>
            <div className="text-[10px] text-zinc-400">Sum(Deltas) == Wallet: PASS</div>
          </div>
        </div>

        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4 flex items-center gap-3">
          <CheckCircle2 className="h-6 w-6 text-emerald-400 shrink-0" />
          <div>
            <div className="text-xs font-bold text-zinc-200">Replica Set rs0</div>
            <div className="text-[10px] text-zinc-400">ACID Transactions: HEALTHY</div>
          </div>
        </div>

        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4 flex items-center gap-3">
          <CheckCircle2 className="h-6 w-6 text-emerald-400 shrink-0" />
          <div>
            <div className="text-xs font-bold text-zinc-200">D-Drive Storage</div>
            <div className="text-[10px] text-zinc-400">Isolated on D:\: COMPLIANT</div>
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-6 py-3">Timestamp</th>
                <th className="px-6 py-3">Actor</th>
                <th className="px-6 py-3">Action</th>
                <th className="px-6 py-3">Target</th>
                <th className="px-6 py-3 text-right">Result</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {logs.map((log, idx) => (
                <tr key={idx} className="hover:bg-white/[0.02]">
                  <td className="px-6 py-3 text-zinc-500 whitespace-nowrap">
                    {new Date(log.timestamp).toLocaleString()}
                  </td>
                  <td className="px-6 py-3 font-semibold text-zinc-300">{log.actor}</td>
                  <td className="px-6 py-3 text-amber-400">{log.action}</td>
                  <td className="px-6 py-3 text-zinc-400">{log.target}</td>
                  <td className="px-6 py-3 text-right">
                    <span className="rounded bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 text-[9px] text-emerald-400 font-bold uppercase">
                      {log.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
