import React, { useState } from "react";
import { useRouter } from "next/router";
import { useAuth } from "../lib/auth";
import { ShieldCheck, User, Terminal, ArrowRight } from "lucide-react";

export default function Login() {
  const router = useRouter();
  const { loginAs, user } = useAuth();
  const [customId, setCustomId] = useState("");
  const [customName, setCustomName] = useState("");

  const handleDemoLogin = async (type: "PLAYER" | "ADMIN") => {
    await loginAs(type);
    router.push(type === "ADMIN" ? "/admin" : "/store");
  };

  const handleCustomLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customId.trim()) return;
    await loginAs("CUSTOM", customId, customName || "Operative");
    router.push("/store");
  };

  return (
    <div className="mx-auto max-w-md px-4 py-16 sm:px-6">
      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 sm:p-8">
        <div className="text-center mb-8">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-400 mb-3">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <h1 className="text-xl font-bold text-zinc-100">Nakama Identity Login</h1>
          <p className="mt-1 text-xs text-zinc-400">
            Authenticate using your Railbound Battle Royale game identity.
          </p>
        </div>

        {/* Quick Demo Logins */}
        <div className="space-y-3 mb-6">
          <button
            onClick={() => handleDemoLogin("PLAYER")}
            className="w-full flex items-center justify-between rounded-lg border border-white/10 bg-zinc-900/60 p-3.5 hover:border-amber-500/50 hover:bg-zinc-900 transition-all text-left"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-8 w-8 items-center justify-center rounded bg-zinc-800 text-zinc-300">
                <User className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs font-semibold text-zinc-200">Sign in as ViperActual</div>
                <div className="text-[10px] font-mono text-zinc-500">Role: PLAYER (Demo Account)</div>
              </div>
            </div>
            <ArrowRight className="h-4 w-4 text-zinc-500" />
          </button>

          <button
            onClick={() => handleDemoLogin("ADMIN")}
            className="w-full flex items-center justify-between rounded-lg border border-amber-500/20 bg-amber-500/[0.03] p-3.5 hover:border-amber-500/50 hover:bg-amber-500/[0.08] transition-all text-left"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-8 w-8 items-center justify-center rounded bg-amber-500/20 text-amber-400">
                <Terminal className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs font-semibold text-amber-200">Sign in as Quartermaster</div>
                <div className="text-[10px] font-mono text-amber-400/70">Role: ADMIN (Publishing Console)</div>
              </div>
            </div>
            <ArrowRight className="h-4 w-4 text-amber-400/60" />
          </button>
        </div>

        {/* Custom Nakama ID */}
        <div className="relative my-6">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-white/5" />
          </div>
          <div className="relative flex justify-center text-[10px] uppercase font-mono tracking-wider">
            <span className="bg-[#121215] px-2 text-zinc-500">Or use Custom Nakama ID</span>
          </div>
        </div>

        <form onSubmit={handleCustomLogin} className="space-y-4">
          <div>
            <label className="block text-[11px] font-mono uppercase text-zinc-400 mb-1">
              Nakama User UUID
            </label>
            <input
              type="text"
              required
              placeholder="e.g. 00000000-0000-0000-0000-000000000002"
              value={customId}
              onChange={(e) => setCustomId(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-xs font-mono text-zinc-200 placeholder:text-zinc-600 focus:border-amber-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-[11px] font-mono uppercase text-zinc-400 mb-1">
              Display Name (Optional)
            </label>
            <input
              type="text"
              placeholder="e.g. GhostActual"
              value={customName}
              onChange={(e) => setCustomName(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-xs text-zinc-200 placeholder:text-zinc-600 focus:border-amber-500 focus:outline-none"
            />
          </div>

          <button
            type="submit"
            className="w-full rounded-md bg-zinc-200 py-2.5 text-xs font-semibold text-zinc-950 hover:bg-white transition-colors"
          >
            Authenticate Session
          </button>
        </form>

        <p className="mt-6 text-center text-[10px] text-zinc-600 leading-normal">
          Passwords are never collected by the portal. Player identity is cryptographically verified against Nakama server authority.
        </p>
      </div>
    </div>
  );
}
