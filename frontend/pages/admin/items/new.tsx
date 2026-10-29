import React, { useState } from "react";
import { useRouter } from "next/router";
import Link from "next/link";
import { api } from "../../../lib/api";
import { ArrowLeft, PlusCircle, Check } from "lucide-react";

export default function NewItemPage() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [sku, setSku] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("ATTACHMENT");
  const [realCoinPrice, setRealCoinPrice] = useState(300);
  const [slot, setSlot] = useState("OPTIC");
  const [minVersion, setMinVersion] = useState("1.0.0");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    const payload = {
      sku: sku.trim(),
      title: title.trim(),
      description: description.trim(),
      category,
      realCoinPrice: Number(realCoinPrice),
      compatibility: {
        slot,
        minimumClientVersion: minVersion,
      },
      currentContentVersion: "1.0.0",
      previewMedia: {
        thumbnailUrl: `/assets/items/${sku.trim()}.png`,
      },
    };

    try {
      await api.adminCreateItem(payload);
      router.push("/admin/items");
    } catch (err: any) {
      setError(err.message || "Failed to create item draft");
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <Link
        href="/admin/items"
        className="inline-flex items-center gap-2 text-xs font-mono text-zinc-400 hover:text-white mb-6 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to Items List
      </Link>

      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 sm:p-8">
        <h1 className="text-xl font-bold text-zinc-100 mb-1">Author New Armory Item Draft</h1>
        <p className="text-xs text-zinc-400 mb-6">
          Draft items are stored securely and remain invisible to players until validated and published.
        </p>

        {error && (
          <div className="mb-6 rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs font-mono">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Unique Item SKU</label>
              <input
                type="text"
                required
                placeholder="e.g. att_optic_thermal_hybrid"
                value={sku}
                onChange={(e) => setSku(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Category</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              >
                <option value="ATTACHMENT">ATTACHMENT</option>
                <option value="OPERATOR_SKIN">OPERATOR_SKIN</option>
                <option value="WEAPON_CAMO">WEAPON_CAMO</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-zinc-400 uppercase text-[10px] mb-1">Title</label>
            <input
              type="text"
              required
              placeholder="e.g. Thermal Hybrid Optic"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-zinc-400 uppercase text-[10px] mb-1">Description</label>
            <textarea
              required
              rows={3}
              placeholder="Operational background and combat utility..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none font-sans"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Real Coin Price</label>
              <input
                type="number"
                min="1"
                required
                value={realCoinPrice}
                onChange={(e) => setRealCoinPrice(Number(e.target.value))}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Equipment Socket</label>
              <input
                type="text"
                required
                value={slot}
                onChange={(e) => setSlot(e.target.value)}
                placeholder="OPTIC / BARREL / OPERATOR"
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Min Client Version</label>
              <input
                type="text"
                required
                value={minVersion}
                onChange={(e) => setMinVersion(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>
          </div>

          <div className="pt-4 flex items-center justify-end gap-3 border-t border-white/5">
            <Link
              href="/admin/items"
              className="rounded-md px-4 py-2 text-xs text-zinc-400 hover:text-white transition-colors"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-md bg-amber-500 px-5 py-2 text-xs font-semibold text-black hover:bg-amber-400 disabled:opacity-50 transition-colors"
            >
              {submitting ? "Saving Draft..." : "Create Draft Item"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
