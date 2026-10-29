import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../../lib/api";
import { Plus, Check, Archive, ArrowLeft, ArrowRight, Package } from "lucide-react";

export default function AdminItemsList() {
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const loadItems = async () => {
    try {
      setLoading(true);
      const data = await api.adminGetItems();
      setItems(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadItems();
  }, []);

  const handlePublish = async (sku: string) => {
    setActionLoading(sku);
    try {
      await api.adminPublishItem(sku);
      await loadItems();
    } catch (err: any) {
      alert("Publish failed: " + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleArchive = async (sku: string) => {
    setActionLoading(sku);
    try {
      await api.adminArchiveItem(sku);
      await loadItems();
    } catch (err: any) {
      alert("Archive failed: " + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Armory Item Catalog Management</h1>
            <p className="text-xs text-zinc-400 mt-0.5">
              Author, validate compatibility, publish, and retire in-game cosmetic assets.
            </p>
          </div>
        </div>

        <Link
          href="/admin/items/new"
          className="inline-flex items-center gap-1.5 rounded-lg bg-amber-500 px-4 py-2 text-xs font-semibold text-black hover:bg-amber-400 transition-colors self-start sm:self-auto"
        >
          <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
          Create New Draft
        </Link>
      </div>

      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-6 py-3">SKU</th>
                <th className="px-6 py-3">Title</th>
                <th className="px-6 py-3">Category</th>
                <th className="px-6 py-3 text-right">Real Coin Price</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {loading ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-zinc-500">
                    Loading catalog assets...
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-zinc-500">
                    No items in catalog.
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.sku} className="hover:bg-white/[0.02] transition-colors">
                    <td className="px-6 py-3 font-semibold text-zinc-200">
                      <Link href={`/admin/items/${item.sku}`} className="hover:text-amber-400 transition-colors">
                        {item.sku}
                      </Link>
                    </td>
                    <td className="px-6 py-3 text-zinc-100">{item.title}</td>
                    <td className="px-6 py-3 text-zinc-400">{item.category}</td>
                    <td className="px-6 py-3 text-right font-bold text-amber-400">
                      {item.realCoinPrice.toLocaleString()} RC
                    </td>
                    <td className="px-6 py-3">
                      <span
                        className={`rounded px-2 py-0.5 text-[9px] uppercase font-bold ${
                          item.publicationStatus === "PUBLISHED"
                            ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                            : item.publicationStatus === "DRAFT"
                            ? "bg-amber-500/10 text-amber-400 border border-amber-500/30"
                            : "bg-zinc-800 text-zinc-400"
                        }`}
                      >
                        {item.publicationStatus}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-right space-x-2">
                      {item.publicationStatus === "DRAFT" && (
                        <button
                          onClick={() => handlePublish(item.sku)}
                          disabled={actionLoading === item.sku}
                          className="rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-2 py-1 text-[10px] hover:bg-emerald-500/30 transition-colors"
                        >
                          Publish Live
                        </button>
                      )}
                      {item.publicationStatus === "PUBLISHED" && (
                        <button
                          onClick={() => handleArchive(item.sku)}
                          disabled={actionLoading === item.sku}
                          className="rounded bg-zinc-800 text-zinc-300 border border-white/10 px-2 py-1 text-[10px] hover:bg-zinc-700 transition-colors"
                        >
                          Archive
                        </button>
                      )}
                      <Link
                        href={`/admin/items/${item.sku}`}
                        className="rounded border border-white/10 px-2 py-1 text-[10px] text-zinc-400 hover:text-white"
                      >
                        Inspect
                      </Link>
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
