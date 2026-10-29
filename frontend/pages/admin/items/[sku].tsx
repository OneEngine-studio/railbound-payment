import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";
import Link from "next/link";
import { api } from "../../../lib/api";
import { ArrowLeft, Check, Archive, Eye } from "lucide-react";

export default function AdminItemDetail() {
  const router = useRouter();
  const { sku } = router.query;
  const [item, setItem] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const loadItem = async () => {
    if (!sku) return;
    try {
      setLoading(true);
      const data = await api.getShopItem(sku as string);
      setItem(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadItem();
  }, [sku]);

  const handlePublish = async () => {
    if (!item) return;
    setActionLoading(true);
    try {
      await api.adminPublishItem(item.sku);
      await loadItem();
    } catch (err: any) {
      alert("Publish failed: " + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleArchive = async () => {
    if (!item) return;
    setActionLoading(true);
    try {
      await api.adminArchiveItem(item.sku);
      await loadItem();
    } catch (err: any) {
      alert("Archive failed: " + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  if (loading || !item) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center text-xs font-mono text-zinc-500">
        Loading item metadata...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
      <Link
        href="/admin/items"
        className="inline-flex items-center gap-2 text-xs font-mono text-zinc-400 hover:text-white mb-6 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to Items Management
      </Link>

      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 sm:p-8 space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-white/5">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-mono text-zinc-500">{item.sku}</span>
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
            </div>
            <h1 className="text-2xl font-bold text-zinc-100">{item.title}</h1>
          </div>

          <div className="flex items-center gap-2">
            {item.publicationStatus === "DRAFT" && (
              <button
                onClick={handlePublish}
                disabled={actionLoading}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-4 py-2 text-xs font-semibold text-black hover:bg-emerald-400 transition-colors"
              >
                <Check className="h-3.5 w-3.5" />
                Publish Live to Store
              </button>
            )}

            {item.publicationStatus === "PUBLISHED" && (
              <button
                onClick={handleArchive}
                disabled={actionLoading}
                className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-zinc-800 px-4 py-2 text-xs font-medium text-zinc-300 hover:bg-zinc-700 transition-colors"
              >
                <Archive className="h-3.5 w-3.5" />
                Retire / Archive
              </button>
            )}

            <Link
              href={`/store/${item.sku}`}
              className="flex items-center gap-1.5 rounded-lg border border-white/10 px-3 py-2 text-xs text-zinc-400 hover:text-white transition-colors"
            >
              <Eye className="h-3.5 w-3.5" />
              Store Preview
            </Link>
          </div>
        </div>

        <div className="space-y-4 text-xs font-mono">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="rounded-xl border border-white/5 bg-zinc-950 p-4">
              <span className="text-[10px] uppercase text-zinc-500">Real Coin Price</span>
              <div className="text-lg font-bold text-amber-400 mt-1">
                {item.realCoinPrice.toLocaleString()} RC
              </div>
            </div>

            <div className="rounded-xl border border-white/5 bg-zinc-950 p-4">
              <span className="text-[10px] uppercase text-zinc-500">Target Socket Slot</span>
              <div className="text-lg font-bold text-zinc-200 mt-1">
                {item.compatibility?.slot || "N/A"}
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-white/5 bg-zinc-950 p-4">
            <span className="text-[10px] uppercase text-zinc-500">Description</span>
            <p className="text-zinc-300 font-sans text-xs mt-1 leading-relaxed">{item.description}</p>
          </div>

          <div className="rounded-xl border border-white/5 bg-zinc-950 p-4">
            <span className="text-[10px] uppercase text-zinc-500">Client Engine Compatibility</span>
            <div className="text-zinc-400 mt-1">
              Minimum Client: <span className="text-zinc-200">{item.compatibility?.minimumClientVersion || "1.0.0"}</span>
            </div>
            <div className="text-zinc-400 mt-0.5">
              Content Version: <span className="text-zinc-200">{item.currentContentVersion || "1.0.0"}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
