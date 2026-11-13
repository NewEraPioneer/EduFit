"use client";
import { ArrowDownLeft, ArrowUpRight, History } from "lucide-react";
import { entryLabels, money, settlement, type Entry } from "../api/paymentApi";
import { SettlementAmount } from "./SettlementAmount";
export function PaymentActivity({
  entries,
  onHistory,
}: {
  entries: Entry[];
  onHistory: () => void;
}) {
  return (
    <section
      aria-label="Hoạt động gần đây"
      className="self-start rounded-2xl border border-line bg-surface p-5"
    >
      <div className="flex items-center gap-2">
        <History className="size-4 text-muted" />
        <h2 className="font-medium">Hoạt động gần đây</h2>
      </div>
      {entries.length ? (
        <ul className="mt-4 divide-y divide-line">
          {entries.slice(0, 4).map((e) => {
            const settled = e.kind === "SESSION_SETTLED";
            const incoming = settled
              ? settlement(e).refund > 0
              : e.available_delta > 0;
            return (
              <li key={e.id} className="flex items-start gap-3 py-4">
                <span
                  className={`mt-0.5 rounded-full p-2 ${
                    incoming
                      ? "bg-success-soft text-success-strong"
                      : "bg-sunken text-muted"
                  }`}
                >
                  {incoming ? (
                    <ArrowDownLeft className="size-4" />
                  ) : (
                    <ArrowUpRight className="size-4" />
                  )}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">
                    {settled
                      ? settlement(e).title
                      : (entryLabels[e.kind] ?? e.kind)}
                  </p>
                  <p className="mt-1 text-xs text-muted">
                    {new Date(e.created_at).toLocaleDateString("vi-VN", {
                      day: "2-digit",
                      month: "2-digit",
                    })}
                  </p>
                </div>
                {settled ? (
                  <SettlementAmount entry={e} className="shrink-0 text-right" />
                ) : (
                  <div className="shrink-0 text-right">
                    <p
                      className={`text-sm font-medium ${
                        e.available_delta > 0 ? "text-success-strong" : ""
                      }`}
                    >
                      {e.available_delta > 0 ? "+" : ""}
                      {money(e.available_delta || e.held_delta)}
                    </p>
                    {e.available_delta === 0 && (
                      <p className="mt-1 text-xs text-muted">Từ tiền giữ</p>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      ) : (
        <div className="py-8 text-center">
          <History className="mx-auto size-7 text-white/75" />
          <p className="mt-3 text-sm text-muted">
            Giao dịch của bạn sẽ xuất hiện tại đây.
          </p>
        </div>
      )}
      <button
        onClick={onHistory}
        className="mt-2 w-full rounded-xl border border-line py-3 text-sm font-medium text-ink-2 hover:bg-paper"
      >
        Xem tất cả giao dịch
      </button>
    </section>
  );
}
