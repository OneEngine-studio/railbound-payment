import React, { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/router";
import { Lock, ShieldCheck, AlertTriangle, ExternalLink } from "lucide-react";

/**
 * Hosted checkout bridge.
 *
 * PayHere expects a classic HTML form POST and validates the browser Referer
 * against the merchant portal's registered domain. This page lives on the
 * frontend origin, fetches the server-signed fields, then auto-submits them.
 * No secret or wallet decision ever happens here.
 */
export default function HostedCheckoutBridge() {
  const router = useRouter();
  const { orderId, token } = router.query;

  const [payload, setPayload] = useState<any | null>(null);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement | null>(null);
  const submittedRef = useRef(false);

  useEffect(() => {
    if (!orderId || !token) return;
    let mounted = true;

    const apiBase = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
    const url = `${apiBase}/payments/hosted/${encodeURIComponent(orderId as string)}?token=${encodeURIComponent(token as string)}`;

    fetch(url)
      .then(async (res) => {
        const body = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(body?.message || `Checkout session unavailable (${res.status})`);
        }
        return body;
      })
      .then((body) => {
        if (!mounted) return;
        setPayload(body);
      })
      .catch((err) => mounted && setError(err.message));

    return () => {
      mounted = false;
    };
  }, [orderId, token]);

  // Auto-submit once the signed fields arrive. If a browser blocks the submit,
  // the visible button remains as a manual fallback.
  useEffect(() => {
    if (!payload || submittedRef.current) return;
    if (payload.method === "POST" && payload.fields && payload.actionUrl) {
      submittedRef.current = true;
      // Small delay lets the "redirecting" state paint before navigation.
      const timer = setTimeout(() => formRef.current?.submit(), 250);
      return () => clearTimeout(timer);
    }
    if (payload.method === "GET" && payload.actionUrl) {
      window.location.href = payload.actionUrl;
    }
  }, [payload]);

  if (error) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 sm:px-6">
        <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-8 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-500/10 text-rose-400 mb-4">
            <AlertTriangle className="h-7 w-7" />
          </div>
          <h1 className="text-lg font-bold text-zinc-100">Checkout Unavailable</h1>
          <p className="mt-2 text-xs text-zinc-400 leading-relaxed">{error}</p>
          <Link
            href="/top-up"
            className="mt-6 inline-flex rounded-lg bg-zinc-800 px-4 py-2 text-xs text-zinc-200 hover:bg-zinc-700"
          >
            Return to Top-Up
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md px-4 py-20 sm:px-6">
      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-8 text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-500/10 text-amber-400 mb-4">
          <Lock className="h-7 w-7" />
        </div>
        <h1 className="text-lg font-bold text-zinc-100">Redirecting to Secure Checkout</h1>
        <p className="mt-2 text-xs text-zinc-400 leading-relaxed">
          You are being handed off to the PayHere PCI-DSS hosted payment page. Railbound never sees or
          stores your card details.
        </p>

        {payload && (
          <div className="mt-6 rounded-xl border border-white/5 bg-zinc-950 p-4 text-left space-y-2 text-xs font-mono">
            <div className="flex justify-between text-zinc-400">
              <span>Coins:</span>
              <span className="font-bold text-amber-400">
                +{Number(payload.coinsToCredit).toLocaleString()} RC
              </span>
            </div>
            <div className="flex justify-between text-zinc-400">
              <span>Amount:</span>
              <span className="text-zinc-200">
                {(Number(payload.amountMinor) / 100).toFixed(2)} {payload.currency}
              </span>
            </div>
            <div className="flex justify-between text-zinc-400">
              <span>Gateway:</span>
              <span className="text-zinc-200 uppercase">{payload.provider}</span>
            </div>
          </div>
        )}

        <form ref={formRef} method="POST" action={payload?.actionUrl || ""} className="hidden">
          {payload?.fields &&
            Object.entries(payload.fields).map(([name, value]) => (
              <input key={name} type="hidden" name={name} value={String(value)} />
            ))}
        </form>

        <button
          type="button"
          onClick={() => formRef.current?.submit()}
          disabled={!payload}
          className="mt-6 inline-flex items-center justify-center gap-2 rounded-lg bg-amber-500 px-4 py-2.5 text-xs font-semibold text-black hover:bg-amber-400 disabled:opacity-50"
        >
          <ExternalLink className="h-3.5 w-3.5" />
          Continue to PayHere
        </button>

        <p className="mt-4 flex items-center justify-center gap-1.5 text-[10px] text-zinc-500">
          <ShieldCheck className="h-3 w-3 text-emerald-400" />
          Signature generated server-side. Card data never touches Railbound.
        </p>
      </div>
    </div>
  );
}
