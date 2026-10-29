import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, Plus, Check, Send } from "lucide-react";

export default function AdminNewsPage() {
  const [announcements, setAnnouncements] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);

  // Form
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [linkedItemSku, setLinkedItemSku] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const loadAnnouncements = async () => {
    try {
      setLoading(true);
      const data = await api.adminGetAnnouncements();
      setAnnouncements(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnnouncements();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.adminCreateAnnouncement({
        title,
        body,
        linkedItemSku: linkedItemSku.trim() || null,
        status: "DRAFT",
      });
      setTitle("");
      setBody("");
      setLinkedItemSku("");
      setShowCreate(false);
      await loadAnnouncements();
    } catch (err: any) {
      alert("Creation failed: " + err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handlePublish = async (id: string) => {
    try {
      await api.adminPublishAnnouncement(id);
      await loadAnnouncements();
    } catch (err: any) {
      alert("Publish failed: " + err.message);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.08] pb-6 mb-8">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-zinc-500 hover:text-white transition-colors">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-100">News & Announcement Broadcasts</h1>
            <p className="text-xs text-zinc-400 mt-0.5">
              Publish tactical patch releases and sector alerts synchronized to game client feeds.
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowCreate(!showCreate)}
          className="inline-flex items-center gap-1.5 rounded-lg bg-amber-500 px-4 py-2 text-xs font-semibold text-black hover:bg-amber-400 transition-colors self-start sm:self-auto"
        >
          <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
          Compose Announcement
        </button>
      </div>

      {showCreate && (
        <div className="mb-8 rounded-2xl border border-white/10 bg-[#121215] p-6">
          <h2 className="text-sm font-bold text-zinc-100 mb-4">Compose Sector Broadcast</h2>
          <form onSubmit={handleCreate} className="space-y-4 text-xs font-mono">
            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Headline</label>
              <input
                type="text"
                required
                placeholder="e.g. Operation Railbound Drop: New Optics & Operator Outfits"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">Briefing Body</label>
              <textarea
                required
                rows={3}
                placeholder="Detailed announcement text..."
                value={body}
                onChange={(e) => setBody(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none font-sans"
              />
            </div>

            <div>
              <label className="block text-zinc-400 uppercase text-[10px] mb-1">
                Linked Item SKU (Optional)
              </label>
              <input
                type="text"
                placeholder="e.g. att_scope_tactical_acog"
                value={linkedItemSku}
                onChange={(e) => setLinkedItemSku(e.target.value)}
                className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowCreate(false)}
                className="rounded-md px-3 py-1.5 text-zinc-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="rounded-md bg-amber-500 px-4 py-1.5 font-semibold text-black hover:bg-amber-400"
              >
                {submitting ? "Saving..." : "Save Draft"}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Announcements Table */}
      <div className="rounded-xl border border-white/[0.08] bg-[#121215] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead className="border-b border-white/5 text-[10px] uppercase text-zinc-500 bg-zinc-950/60">
              <tr>
                <th className="px-6 py-3">Publish Date</th>
                <th className="px-6 py-3">Headline</th>
                <th className="px-6 py-3">Featured SKU</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-zinc-300">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-zinc-500">
                    Loading broadcasts...
                  </td>
                </tr>
              ) : announcements.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-zinc-500">
                    No announcements created.
                  </td>
                </tr>
              ) : (
                announcements.map((a) => (
                  <tr key={a.announcementId} className="hover:bg-white/[0.02]">
                    <td className="px-6 py-3 text-zinc-500 whitespace-nowrap">
                      {new Date(a.publishAt).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-3 font-semibold text-zinc-100 max-w-sm truncate">{a.title}</td>
                    <td className="px-6 py-3 text-amber-400 font-mono">{a.linkedItemSku || "--"}</td>
                    <td className="px-6 py-3">
                      <span
                        className={`rounded px-2 py-0.5 text-[9px] uppercase font-bold ${
                          a.status === "PUBLISHED"
                            ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                            : "bg-amber-500/10 text-amber-400 border border-amber-500/30"
                        }`}
                      >
                        {a.status}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-right">
                      {a.status !== "PUBLISHED" && (
                        <button
                          onClick={() => handlePublish(a.announcementId)}
                          className="rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-2.5 py-1 text-[10px] hover:bg-emerald-500/30"
                        >
                          Broadcast Live
                        </button>
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
