import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../lib/api";
import { History, ShoppingBag, ArrowLeft } from "lucide-react";

export default function PurchaseHistoryPage() {
  const [purchases, setPurchases] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getMyPurchases()
      .then(setPurchases)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex items-center gap-3 mb-6">
        <Link href="/wallet" className="text-zinc-500 hover:text-white transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <h1 className="text-xl font-bold text-zinc-100">Armory Purchase History</h1>
      </div>

      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-6 py-3">Timestamp</th>
                <th className="px-6 py-3">Purchase ID</th>
                <th className="px-6 py-3">Item SKU</th>
                <th className="px-6 py-3 text-right">Coins Spent</th>
                <th className="px-6 py-3 text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-zinc-500">
                    Loading purchase history...
                  </td>
                </tr>
              ) : purchases.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-zinc-500">
                    No armory purchases recorded yet.
                  </td>
                </tr>
              ) : (
                purchases.map((p) => (
                  <tr key={p.purchaseId} className="hover:bg-white/[0.02] transition-colors">
                    <td className="px-6 py-3 text-zinc-500 whitespace-nowrap">
                      {new Date(p.createdAt).toLocaleString()}
                    </td>
                    <td className="px-6 py-3 text-zinc-400">{p.purchaseId}</td>
                    <td className="px-6 py-3 font-semibold text-zinc-200">{p.itemSku}</td>
                    <td className="px-6 py-3 text-right font-bold text-amber-400">
                      -{p.priceSnapshot.toLocaleString()} RC
                    </td>
                    <td className="px-6 py-3 text-right">
                      <span className="rounded bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 text-[9px] uppercase font-bold text-emerald-400">
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
