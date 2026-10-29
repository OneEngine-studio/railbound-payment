import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "../lib/api";
import { Coins, ArrowRight, Shield, Zap, Sparkles, Crosshair } from "lucide-react";

export default function Home() {
  const [bundles, setBundles] = useState<any[]>([]);
  const [news, setNews] = useState<any[]>([]);

  useEffect(() => {
    api.getBundles().then(setBundles).catch(() => {});
    api.getNews().then(setNews).catch(() => {});
  }, []);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
      {/* Minimalist Tactical Hero */}
      <section className="relative overflow-hidden rounded-2xl border border-white/[0.08] bg-zinc-950/60 p-8 sm:p-14">
        <div className="absolute inset-0 bg-gradient-to-r from-amber-500/[0.03] to-transparent pointer-events-none" />
        
        <div className="relative max-w-2xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-amber-500/30 bg-amber-500/10 px-3 py-1 text-xs font-mono text-amber-400 mb-6">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
            <span>AUTHORITATIVE REAL COIN ECONOMY ACTIVE</span>
          </div>

          <h1 className="text-3xl sm:text-5xl font-bold tracking-tight text-zinc-100">
            Equip Your Operative for the Frontline.
          </h1>

          <p className="mt-4 text-sm sm:text-base text-zinc-400 leading-relaxed">
            The official Railbound Battle Royale payment gateway and asset release portal. Securely top-up premium Real Coins, unlock tactical attachments, and synchronize your in-game armory seamlessly with Unity.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link
              href="/store"
              className="inline-flex items-center gap-2 rounded-lg bg-zinc-100 px-5 py-2.5 text-xs font-semibold text-zinc-950 hover:bg-zinc-200 transition-colors"
            >
              Browse Armory Store
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>

            <Link
              href="/top-up"
              className="inline-flex items-center gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-5 py-2.5 text-xs font-semibold text-amber-300 hover:bg-amber-500/20 transition-colors"
            >
              <Coins className="h-4 w-4 text-amber-400" />
              Top-Up Real Coins
            </Link>
          </div>
        </div>
      </section>

      {/* Real Coin Featured Bundle Showcase (PDF Example: bundle_rc_1000) */}
      <section className="mt-14">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-base font-semibold text-zinc-100">Featured Coin Bundles</h2>
            <p className="text-xs text-zinc-500">Transparent pricing. No hidden fees or predatory mechanics.</p>
          </div>
          <Link href="/top-up" className="text-xs font-medium text-amber-400 hover:text-amber-300 flex items-center gap-1">
            View All Bundles <ArrowRight className="h-3 w-3" />
          </Link>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {bundles.slice(0, 3).map((bundle) => (
            <div
              key={bundle.sku}
              className="group relative flex flex-col justify-between rounded-xl border border-white/[0.08] bg-[#121215] p-5 hover:border-white/[0.18] transition-all"
            >
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-mono font-medium text-zinc-400 uppercase tracking-wider">
                    {bundle.sku}
                  </span>
                  {bundle.badgeText && (
                    <span className="rounded bg-amber-500/15 border border-amber-500/30 px-2 py-0.5 text-[10px] font-mono font-medium text-amber-400">
                      {bundle.badgeText}
                    </span>
                  )}
                </div>

                <h3 className="text-lg font-bold text-zinc-100">{bundle.title}</h3>
                
                <div className="mt-4 flex items-baseline gap-2">
                  <span className="text-3xl font-extrabold text-amber-400 font-mono">
                    {bundle.totalCoins.toLocaleString()}
                  </span>
                  <span className="text-xs font-mono text-zinc-400">REAL COINS</span>
                </div>

                {bundle.bonusCoins > 0 ? (
                  <p className="mt-1 text-xs font-mono text-emerald-400">
                    Includes {bundle.bonusCoins.toLocaleString()} Bonus Coins
                  </p>
                ) : (
                  <p className="mt-1 text-xs text-zinc-500">Standard Tier</p>
                )}
              </div>

              <div className="mt-6 pt-4 border-t border-white/5 flex items-center justify-between">
                <span className="text-sm font-semibold text-zinc-200">
                  ${(bundle.fiatAmountMinor / 100).toFixed(2)} USD
                </span>
                <Link
                  href="/top-up"
                  className="rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700 transition-colors"
                >
                  Select
                </Link>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Featured Armory Drop Items */}
      <section className="mt-14">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-base font-semibold text-zinc-100">Armory Drops</h2>
            <p className="text-xs text-zinc-500">Direct Real Coin acquisitions for weapon attachments and cosmetics.</p>
          </div>
          <Link href="/store" className="text-xs font-medium text-amber-400 hover:text-amber-300 flex items-center gap-1">
            Visit Store <ArrowRight className="h-3 w-3" />
          </Link>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400">ATTACHMENT</span>
            <h3 className="mt-3 text-base font-bold text-zinc-100">Tactical ACOG (4x)</h3>
            <p className="mt-1 text-xs text-zinc-400 line-clamp-2">Combat optic with illuminated reticle and 4x magnification.</p>
            <div className="mt-4 flex items-center justify-between">
              <span className="text-sm font-bold font-mono text-amber-400">450 RC</span>
              <Link href="/store/att_scope_tactical_acog" className="text-xs text-zinc-400 hover:text-white">
                Inspect
              </Link>
            </div>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400">OPERATOR SKIN</span>
            <h3 className="mt-3 text-base font-bold text-zinc-100">Shadow Operative</h3>
            <p className="mt-1 text-xs text-zinc-400 line-clamp-2">Radar-dampening tactical stealth uniform for night raids.</p>
            <div className="mt-4 flex items-center justify-between">
              <span className="text-sm font-bold font-mono text-amber-400">1,200 RC</span>
              <Link href="/store/skin_op_shadow_operative" className="text-xs text-zinc-400 hover:text-white">
                Inspect
              </Link>
            </div>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <span className="rounded bg-zinc-800 px-2 py-0.5 text-[10px] font-mono text-zinc-400">WEAPON CAMO</span>
            <h3 className="mt-3 text-base font-bold text-zinc-100">Gold Damascus Camo</h3>
            <p className="mt-1 text-xs text-zinc-400 line-clamp-2">Pattern-welded steel finish inlaid with pure gold accents.</p>
            <div className="mt-4 flex items-center justify-between">
              <span className="text-sm font-bold font-mono text-amber-400">650 RC</span>
              <Link href="/store/skin_wp_gold_damascus" className="text-xs text-zinc-400 hover:text-white">
                Inspect
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Latest Announcements */}
      {news.length > 0 && (
        <section className="mt-14">
          <h2 className="text-base font-semibold text-zinc-100 mb-4">Latest Sector News</h2>
          <div className="space-y-3">
            {news.map((item) => (
              <div
                key={item.announcementId}
                className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg border border-white/[0.08] bg-[#121215] p-4 hover:border-white/[0.14] transition-colors"
              >
                <div>
                  <h3 className="text-sm font-medium text-zinc-200">{item.title}</h3>
                  <p className="text-xs text-zinc-400 mt-0.5 line-clamp-1">{item.body}</p>
                </div>
                <Link
                  href={`/news/${item.announcementId}`}
                  className="text-xs font-mono text-amber-400 hover:text-amber-300 self-start sm:self-auto"
                >
                  Read &rarr;
                </Link>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
