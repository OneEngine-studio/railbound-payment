import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../../lib/api";
import { Newspaper, ArrowRight, Tag } from "lucide-react";

export default function NewsIndex() {
  const [news, setNews] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getNews()
      .then(setNews)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="border-b border-white/[0.08] pb-6 mb-8">
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Game Announcements & Patch Releases</h1>
        <p className="text-xs text-zinc-400 mt-1">
          Sector intelligence, weapon balance notes, and new cosmetic shipment releases.
        </p>
      </div>

      {loading ? (
        <div className="py-20 text-center text-xs font-mono text-zinc-500">Loading sector feed...</div>
      ) : news.length === 0 ? (
        <div className="py-20 text-center text-xs text-zinc-500">No active announcements.</div>
      ) : (
        <div className="space-y-4">
          {news.map((item) => (
            <article
              key={item.announcementId}
              className="rounded-xl border border-white/[0.08] bg-[#121215] p-6 hover:border-white/[0.16] transition-all"
            >
              <div className="flex items-center gap-2 text-[10px] font-mono text-zinc-500 mb-2">
                <span>{new Date(item.publishAt).toLocaleDateString()}</span>
                <span>•</span>
                <span className="text-amber-400/80 uppercase">{item.status}</span>
              </div>

              <h2 className="text-base font-bold text-zinc-100 hover:text-amber-400 transition-colors">
                <Link href={`/news/${item.announcementId}`}>{item.title}</Link>
              </h2>

              <p className="mt-2 text-xs text-zinc-400 leading-relaxed">{item.body}</p>

              <div className="mt-4 pt-4 border-t border-white/5 flex items-center justify-between">
                {item.linkedItemSku ? (
                  <Link
                    href={`/store/${item.linkedItemSku}`}
                    className="inline-flex items-center gap-1.5 rounded bg-zinc-800 px-2.5 py-1 text-[11px] font-mono text-amber-300 hover:bg-zinc-700 transition-colors"
                  >
                    <Tag className="h-3 w-3 text-amber-400" />
                    Inspect Featured SKU: {item.linkedItemSku}
                  </Link>
                ) : (
                  <span />
                )}

                <Link
                  href={`/news/${item.announcementId}`}
                  className="inline-flex items-center gap-1 text-xs font-mono text-zinc-400 hover:text-white transition-colors"
                >
                  Full Briefing <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
