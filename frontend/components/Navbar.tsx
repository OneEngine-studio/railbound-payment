import React from "react";
import Link from "next/link";
import { useRouter } from "next/router";
import { useAuth } from "../lib/auth";
import { Coins, Plus, Shield, User, LogOut, Terminal, Compass } from "lucide-react";

export const Navbar: React.FC = () => {
  const router = useRouter();
  const { user, wallet, isAdmin, loginAs, logout } = useAuth();

  const navLinks = [
    { label: "Store", href: "/store" },
    { label: "Top-Up", href: "/top-up" },
    { label: "Wallet", href: "/wallet" },
    { label: "Inventory", href: "/inventory" },
    { label: "News", href: "/news" },
    { label: "Support", href: "/support" },
  ];

  const isActive = (href: string) => {
    if (href === "/store" && router.pathname.startsWith("/store")) return true;
    return router.pathname === href;
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b border-white/[0.08] bg-[#09090b]/90 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
        {/* Brand */}
        <div className="flex items-center gap-8">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="flex h-7 w-7 items-center justify-center rounded bg-zinc-800 border border-white/10 group-hover:border-amber-500/50 transition-colors">
              <Compass className="h-4 w-4 text-amber-500" />
            </div>
            <div className="flex flex-col">
              <span className="text-xs font-semibold tracking-wider text-zinc-100 uppercase">Railbound</span>
              <span className="text-[10px] tracking-widest text-zinc-500 font-mono -mt-0.5">PORTAL</span>
            </div>
          </Link>

          {/* Navigation Links */}
          <nav className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  isActive(link.href)
                    ? "bg-zinc-800/80 text-zinc-100"
                    : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900"
                }`}
              >
                {link.label}
              </Link>
            ))}
            {isAdmin && (
              <Link
                href="/admin"
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  router.pathname.startsWith("/admin")
                    ? "bg-amber-500/10 text-amber-400 border border-amber-500/30"
                    : "text-amber-500/80 hover:text-amber-400 hover:bg-zinc-900"
                }`}
              >
                <Terminal className="h-3 w-3" />
                Admin
              </Link>
            )}
          </nav>
        </div>

        {/* Currency & User Controls */}
        <div className="flex items-center gap-3">
          {/* Authoritative Real Coins Counter */}
          <div className="flex items-center gap-1 rounded-full border border-amber-500/30 bg-amber-500/[0.06] py-1 pl-2.5 pr-1 text-xs">
            <Coins className="h-3.5 w-3.5 text-amber-400" />
            <span className="font-mono font-medium text-amber-200 mr-1">
              {wallet ? wallet.realCoins.toLocaleString() : "--"}
            </span>
            <span className="text-[10px] font-mono uppercase tracking-wider text-amber-400/70 mr-1.5">RC</span>
            <Link
              href="/top-up"
              className="flex h-5 w-5 items-center justify-center rounded-full bg-amber-500 text-black hover:bg-amber-400 transition-colors"
              title="Top up Real Coins"
            >
              <Plus className="h-3 w-3 stroke-[2.5]" />
            </Link>
          </div>

          {/* Reserved Game Coins (Disabled Placeholder per policy) */}
          <div
            className="hidden sm:flex items-center gap-1 rounded-full border border-white/5 bg-zinc-900/60 px-2.5 py-1 text-xs text-zinc-500 cursor-not-allowed select-none"
            title="Game Coins (Soft Currency) - Deferred"
          >
            <span className="font-mono">--</span>
            <span className="text-[10px] font-mono tracking-wider">GC</span>
          </div>

          <div className="h-4 w-px bg-white/10" />

          {/* User Account / Role Switcher */}
          {user ? (
            <div className="flex items-center gap-2">
              <Link
                href="/profile"
                className="flex items-center gap-1.5 px-2 py-1 rounded text-xs text-zinc-300 hover:text-white transition-colors"
              >
                <User className="h-3.5 w-3.5 text-zinc-400" />
                <span className="font-medium max-w-[100px] truncate">{user.displayName}</span>
                <span className="rounded bg-zinc-800 px-1 py-0.5 text-[9px] font-mono text-zinc-400 uppercase">
                  {user.role}
                </span>
              </Link>

              {/* Demo Fast-Switch Toggle */}
              <button
                onClick={() => loginAs(user.role === "ADMIN" ? "PLAYER" : "ADMIN")}
                className="rounded border border-white/10 px-2 py-1 text-[10px] font-mono text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors"
                title="Switch Demo Role"
              >
                Switch to {user.role === "ADMIN" ? "Player" : "Admin"}
              </button>

              <button
                onClick={logout}
                className="p-1 rounded text-zinc-400 hover:text-zinc-200 transition-colors"
                title="Sign out"
              >
                <LogOut className="h-3.5 w-3.5" />
              </button>
            </div>
          ) : (
            <Link
              href="/login"
              className="rounded bg-zinc-800 px-3 py-1 text-xs font-medium text-zinc-200 hover:bg-zinc-700 transition-colors"
            >
              Sign In
            </Link>
          )}
        </div>
      </div>
    </header>
  );
};
