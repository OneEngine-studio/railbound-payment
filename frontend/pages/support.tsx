import React, { useState } from "react";
import { ShieldCheck, HelpCircle, Send, Check } from "lucide-react";

export default function SupportPage() {
  const [submitted, setSubmitted] = useState(false);
  const [topic, setTopic] = useState("TOP_UP_INQUIRY");
  const [details, setDetails] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      setDetails("");
    }, 3000);
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <div className="border-b border-white/[0.08] pb-6 mb-8">
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Player Support & Commercial Policies</h1>
        <p className="text-xs text-zinc-400 mt-1">
          Assistance with top-up fulfillment, billing reconciliation, and consumer disclosures.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Support Disclosures */}
        <div className="space-y-6 text-xs text-zinc-400 leading-relaxed">
          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2 flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-emerald-400" />
              Refund & Chargeback Policy
            </h2>
            <p>
              Fiat top-ups may be refunded through official payment provider dispute channels if the purchased Real Coins remain unspent. If coins have been consumed on Armory items, our finance support team reviews the account for item revocation rather than driving the wallet into an unauthorized negative balance.
            </p>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2 flex items-center gap-2">
              <HelpCircle className="h-4 w-4 text-amber-400" />
              Webhook Delays & Reconciliation
            </h2>
            <p>
              Under extreme network latency, hosted payment gateways may delay callback delivery. Our background reconciliation job polls provider transaction ledgers every 60 minutes to resolve and credit pending orders automatically.
            </p>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-[#121215] p-5">
            <h2 className="text-sm font-bold text-zinc-100 mb-2">Fair Play Guarantee</h2>
            <p>
              Attachments and operator skins available in the Armory are balanced for competitive parity. Competitive advantage in Railbound Battle Royale is never gatekept behind fiat spending.
            </p>
          </div>
        </div>

        {/* Support Request Form */}
        <div className="rounded-2xl border border-white/[0.08] bg-[#121215] p-6">
          <h2 className="text-sm font-bold text-zinc-100 mb-4">Submit Operative Inquiry</h2>

          {submitted ? (
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-center text-xs text-emerald-300">
              <Check className="mx-auto h-6 w-6 text-emerald-400 mb-2" />
              <p className="font-semibold">Inquiry Ticket Dispatched</p>
              <p className="mt-1 text-[11px] text-zinc-400">
                Quartermaster finance team has received your correlation request.
              </p>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4 text-xs font-mono">
              <div>
                <label className="block text-zinc-400 uppercase text-[10px] mb-1">Inquiry Topic</label>
                <select
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none"
                >
                  <option value="TOP_UP_INQUIRY">Real Coin Top-Up Inquiry</option>
                  <option value="ITEM_EQUIP_ISSUE">Armory Item Equip Discrepancy</option>
                  <option value="BILLING_REFUND">Billing / Refund Review</option>
                  <option value="ACCOUNT_SYNC">Unity / Nakama Sync Issue</option>
                </select>
              </div>

              <div>
                <label className="block text-zinc-400 uppercase text-[10px] mb-1">
                  Order ID or Item SKU (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. ord_550b1207... or att_scope_tactical_acog"
                  className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none text-xs"
                />
              </div>

              <div>
                <label className="block text-zinc-400 uppercase text-[10px] mb-1">Issue Details</label>
                <textarea
                  required
                  rows={4}
                  value={details}
                  onChange={(e) => setDetails(e.target.value)}
                  placeholder="Describe your issue or order inquiry..."
                  className="w-full rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-zinc-200 focus:border-amber-500 focus:outline-none text-xs font-sans"
                />
              </div>

              <button
                type="submit"
                className="w-full flex items-center justify-center gap-2 rounded-md bg-zinc-200 py-2.5 text-xs font-semibold text-zinc-950 hover:bg-white transition-colors"
              >
                <Send className="h-3.5 w-3.5" />
                Submit Support Ticket
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
