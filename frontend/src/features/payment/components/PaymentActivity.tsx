"use client";
import { ArrowDownLeft, ArrowUpRight, History } from "lucide-react";
import { entryLabels, money, type Entry } from "../api/paymentApi";
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
      className="self-start rounded-2xl border border-stone-200 bg-white p-5"
    >
      <div className="flex items-center gap-2">
        <History className="size-4 text-neutral-500" />
        <h2 className="font-bold">Hoạt động gần đây</h2>
      </div>
      {entries.length ? (
        <ul className="mt-4 divide-y divide-stone-100">
          {entries.slice(0, 4).map((e) => (
            <li key={e.id} className="flex items-start gap-3 py-4">
              <span
                className={`mt-0.5 rounded-full p-2 ${
                  e.available_delta > 0
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-stone-100 text-neutral-500"
                }`}
              >
                {e.available_delta > 0 ? (
                  <ArrowDownLeft className="size-4" />
                ) : (
                  <ArrowUpRight className="size-4" />
                )}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">
                  {entryLabels[e.kind] ?? e.kind}
                </p>
                <p className="mt-1 text-xs text-neutral-500">
                  {new Date(e.created_at).toLocaleDateString("vi-VN", {
                    day: "2-digit",
                    month: "2-digit",
                  })}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p
                  className={`text-sm font-semibold ${
                    e.available_delta > 0 ? "text-emerald-700" : ""
                  }`}
                >
                  {e.available_delta > 0 ? "+" : ""}
                  {money(e.available_delta || e.held_delta)}
                </p>
                {e.available_delta === 0 && (
                  <p className="mt-1 text-xs text-neutral-500">Từ tiền giữ</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <div className="py-8 text-center">
          <History className="mx-auto size-7 text-stone-300" />
          <p className="mt-3 text-sm text-neutral-500">
            Giao dịch của bạn sẽ xuất hiện tại đây.
          </p>
        </div>
      )}
      <button
        onClick={onHistory}
        className="mt-2 w-full rounded-xl border border-stone-200 py-3 text-sm font-semibold text-neutral-700 hover:bg-stone-50"
      >
        Xem tất cả giao dịch
      </button>
    </section>
  );
}
