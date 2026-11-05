import React from "react";
import Link from "next/link";
import { ShieldCheck, Database, HardDrive, CheckCircle2 } from "lucide-react";

export const Footer: React.FC = () => {
  return (
    <footer className="mt-auto border-t border-white/[0.08] bg-[#09090b] text-zinc-500 text-xs">
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <span className="font-semibold text-zinc-400">RAILBOUND BATTLE ROYALE</span>
            <span className="text-zinc-600">|</span>
            <span>Payment & Asset Release Portal</span>
          </div>

          <div className="flex items-center gap-4 text-[11px] font-mono">
            <div className="flex items-center gap-1.5 text-emerald-400/90">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>MongoDB rs0: Active (D:)</span>
            </div>
            <div className="flex items-center gap-1.5 text-zinc-400">
              <HardDrive className="h-3 w-3 text-zinc-500" />
              <span>Root: D:\MyProjects</span>
            </div>
            <div className="flex items-center gap-1.5 text-zinc-400">
              <ShieldCheck className="h-3 w-3 text-amber-500/80" />
              <span>HMAC-SHA256 Webhooks</span>
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-col sm:flex-row items-center justify-between gap-2 border-t border-white/5 pt-4 text-[11px] text-zinc-600">
          <p>© 2026 Railbound Systems. Authoritative wallet & double-entry ledger engine.</p>
          <div className="flex items-center gap-4">
            <Link href="/support" className="hover:text-zinc-400 transition-colors">
              Refund & Commerce Policy
            </Link>
            <a
              href="http://localhost:8080/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="hover:text-zinc-400 transition-colors"
            >
              OpenAPI Swagger
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};
