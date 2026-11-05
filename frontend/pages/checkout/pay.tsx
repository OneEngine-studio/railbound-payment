import React, { useEffect, useRef, useState } from "react";
import { useRouter } from "next/router";
import { api } from "../../lib/api";
import { Lock, ShieldCheck, AlertTriangle } from "lucide-react";

/**
 * PayHere hands-off step. Opened from the top-up page (or from the Unity client's external browser)
 * with ?orderId=...&ct=<one-time checkout token>. PayHere requires the customer's contact details,
 * so we collect them here, ask the server for the signed form (the server computes the price and
 * hash - nothing here can change what the customer pays) and POST the customer to PayHere.
 */
export default function CheckoutPay() {
  const router = useRouter();
  const { orderId, ct } = router.query;

  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    email: "",
    phone: "",
    address: "",
    city: "",
    country: "Sri Lanka",
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement | null>(null);
  const [hosted, setHosted] = useState<{ action: string; fields: Record<string, string> } | null>(null);

  // Once the signed fields arrive, submit the hidden form to PayHere.
  useEffect(() => {
    if (hosted && formRef.current) {
      formRef.current.submit();
    }
  }, [hosted]);

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!orderId || !ct) {
      setError("This checkout link is incomplete. Please start the top-up again.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const result = await api.getHostedForm(orderId as string, ct as string, form);
      setHosted(result);
    } catch (err: any) {
      setError(err.message || "Could not open the payment page. Please start the top-up again.");
      setSubmitting(false);
    }
  };

  const input =
    "w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-sm text-zinc-200 focus:border-amber-500 focus:outline-none";
  const label = "block text-zinc-400 uppercase text-[10px] mb-1 font-mono";

  if (router.isReady && (!orderId || !ct)) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center text-sm text-zinc-300">
        <AlertTriangle className="mx-auto h-8 w-8 text-amber-400 mb-3" />
        This checkout link is missing information. Please start your top-up again from the store.
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-12 sm:px-6">
      <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6 shadow-xl">
        <div className="flex items-center gap-2 text-xs font-mono text-amber-400 mb-1">
          <ShieldCheck className="h-4 w-4" />
          <span>SECURE CHECKOUT VIA PAYHERE</span>
        </div>
        <h1 className="text-xl font-bold text-zinc-100">Billing details</h1>
        <p className="mt-1 mb-5 text-xs text-zinc-400 leading-relaxed">
          PayHere requires your contact details. They are passed to PayHere and are not stored by Railbound. You will enter your card details on PayHere&apos;s own page.
        </p>

        <form onSubmit={onSubmit} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={label} htmlFor="firstName">First name</label>
              <input id="firstName" required maxLength={50} className={input} value={form.firstName} onChange={set("firstName")} autoComplete="given-name" />
            </div>
            <div>
              <label className={label} htmlFor="lastName">Last name</label>
              <input id="lastName" required maxLength={50} className={input} value={form.lastName} onChange={set("lastName")} autoComplete="family-name" />
            </div>
          </div>
          <div>
            <label className={label} htmlFor="email">Email</label>
            <input id="email" type="email" required maxLength={254} className={input} value={form.email} onChange={set("email")} autoComplete="email" />
          </div>
          <div>
            <label className={label} htmlFor="phone">Phone</label>
            <input id="phone" type="tel" required maxLength={20} placeholder="0771234567" className={input} value={form.phone} onChange={set("phone")} autoComplete="tel" />
          </div>
          <div>
            <label className={label} htmlFor="address">Address</label>
            <input id="address" required maxLength={120} className={input} value={form.address} onChange={set("address")} autoComplete="street-address" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={label} htmlFor="city">City</label>
              <input id="city" required maxLength={60} className={input} value={form.city} onChange={set("city")} autoComplete="address-level2" />
            </div>
            <div>
              <label className={label} htmlFor="country">Country</label>
              <input id="country" required maxLength={60} className={input} value={form.country} onChange={set("country")} autoComplete="country-name" />
            </div>
          </div>

          {error && (
            <div className="rounded-md border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">{error}</div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full flex items-center justify-center gap-2 rounded-lg bg-amber-500 py-2.5 text-xs font-semibold text-black hover:bg-amber-400 disabled:opacity-50 transition-colors"
          >
            <Lock className="h-3.5 w-3.5" />
            <span>{submitting ? "Opening PayHere..." : "Continue to PayHere"}</span>
          </button>
        </form>
      </div>

      {/* Hidden form that carries the server-signed fields to PayHere. */}
      {hosted && (
        <form ref={formRef} method="POST" action={hosted.action} style={{ display: "none" }}>
          {Object.entries(hosted.fields).map(([name, value]) => (
            <input key={name} type="hidden" name={name} value={value} />
          ))}
        </form>
      )}
    </div>
  );
}
