import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";
import Link from "next/link";
import { api } from "../../lib/api";
import { ArrowLeft, Tag, Calendar, User } from "lucide-react";

export default function AnnouncementDetail() {
  const router = useRouter();
  const { id } = router.query;
  const [announcement, setAnnouncement] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    api.getAnnouncement(id as string)
      .then(setAnnouncement)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [id]);

  if (loading || !announcement) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center text-xs font-mono text-zinc-500">
        Loading intelligence briefing...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <Link
        href="/news"
        className="inline-flex items-center gap-2 text-xs font-mono text-zinc-400 hover:text-white mb-6 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to News Feed
      </Link>

      <article className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 sm:p-10">
        <div className="flex flex-wrap items-center gap-4 text-xs font-mono text-zinc-500 mb-4 pb-4 border-b border-white/5">
          <div className="flex items-center gap-1.5">
            <Calendar className="h-3.5 w-3.5" />
            <span>{new Date(announcement.publishAt).toLocaleDateString()}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <User className="h-3.5 w-3.5" />
            <span>Author: {announcement.createdBy}</span>
          </div>
          <span className="rounded bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 text-[10px] text-amber-400">
            {announcement.status}
          </span>
        </div>

        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">{announcement.title}</h1>

        <div className="mt-6 text-sm text-zinc-300 leading-relaxed space-y-4">
          <p>{announcement.body}</p>
        </div>

        {announcement.linkedItemSku && (
          <div className="mt-8 rounded-xl border border-white/5 bg-zinc-950 p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded bg-amber-500/10 text-amber-400">
                <Tag className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs font-bold text-zinc-200">Featured In-Game Armory Item</div>
                <div className="text-[10px] font-mono text-zinc-400">{announcement.linkedItemSku}</div>
              </div>
            </div>
            <Link
              href={`/store/${announcement.linkedItemSku}`}
              className="rounded-md bg-amber-500 px-3 py-1.5 text-xs font-semibold text-black hover:bg-amber-400 transition-colors"
            >
              Inspect in Store
            </Link>
          </div>
        )}
      </article>
    </div>
  );
}
