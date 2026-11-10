"use client";
import { useCallback, useEffect, useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
} from "lucide-react";
import {
  entryLabels,
  money,
  paymentApi,
  paymentError,
  type Checkout,
  type Overview,
} from "../api/paymentApi";
import { PaymentDialog } from "./PaymentDialog";
import { checkoutFromTopup } from "./WalletPanel";
import { TopupCheckoutDialog } from "./TopupCheckoutDialog";

export function PaymentHistoryDialog({
  onClose,
  execute,
}: {
  onClose: () => void;
  execute: (work: () => Promise<unknown>) => Promise<boolean>;
}) {
  const [offset, setOffset] = useState(0);
  const [tab, setTab] = useState<"entries" | "requests">("entries");
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [checkout, setCheckout] = useState<Checkout | null>(null);
  useEffect(() => {
    let alive = true;
    async function load() {
      setLoading(true);
      try {
        const response = await paymentApi.wallet(offset);
        if (alive) {
          setData(response.data ?? null);
          setError(null);
        }
      } catch (e) {
        if (alive) setError(paymentError(e));
      } finally {
        if (alive) setLoading(false);
      }
    }
    void load();
    return () => {
      alive = false;
    };
  }, [offset, reload]);
  const run = useCallback(
    async (work: () => Promise<unknown>) => {
      const ok = await execute(work);
      if (ok) setReload((n) => n + 1);
      return ok;
    },
    [execute],
  );
  const hasNext =
    tab === "entries"
      ? (data?.entries.length ?? 0) >= 50
      : Math.max(data?.topups.length ?? 0, data?.withdrawals.length ?? 0) >= 50;
  return (
    <PaymentDialog title="Giao dịch của tôi" onClose={onClose} wide>
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-1 rounded-xl bg-sunken p-1">
            {(
              [
                ["entries", "Biến động số dư"],
                ["requests", "Yêu cầu nạp / rút"],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                aria-pressed={tab === key}
                onClick={() => {
                  setTab(key);
                  setOffset(0);
                }}
                className={`rounded-lg px-3 py-2 text-sm font-medium ${
                  tab === key ? "bg-surface shadow-sm" : "text-muted"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          <button
            aria-label="Làm mới giao dịch"
            disabled={loading}
            onClick={() => setReload((n) => n + 1)}
            className="rounded-lg border border-line p-2"
          >
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
        {error && (
          <p
            role="alert"
            className="rounded-xl bg-danger-soft p-3 text-sm text-danger-strong"
          >
            {error}
          </p>
        )}
        {loading ? (
          <p
            role="status"
            className="py-8 text-center text-sm text-muted"
          >
            Đang tải giao dịch…
          </p>
        ) : tab === "entries" ? (
          <div className="overflow-x-auto">
            {data?.entries.length ? (
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-line text-xs text-muted">
                    <th className="pb-3 pr-4 font-medium">Giao dịch</th>
                    <th className="pb-3 pr-4 text-right font-medium">
                      Khả dụng
                    </th>
                    <th className="pb-3 text-right font-medium">Đang giữ</th>
                  </tr>
                </thead>
                <tbody>
                  {data.entries.map((e) => (
                    <tr key={e.id} className="border-b border-line">
                      <td className="py-3 pr-4">
                        <p className="font-medium">
                          {entryLabels[e.kind] ?? e.kind}
                        </p>
                        <p className="mt-1 text-xs text-muted">
                          {new Date(e.created_at).toLocaleString("vi-VN")}
                        </p>
                      </td>
                      <td
                        className={`whitespace-nowrap py-3 pr-4 text-right font-medium ${
                          e.available_delta > 0 ? "text-success-strong" : ""
                        }`}
                      >
                        {e.available_delta > 0 ? "+" : ""}
                        {money(e.available_delta)}
                      </td>
                      <td className="whitespace-nowrap py-3 text-right text-muted">
                        {money(e.held_delta)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p className="py-8 text-center text-sm text-muted">
                Chưa có giao dịch trên trang này.
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            {data?.topups.map((t) => (
              <div
                key={t.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line p-4"
              >
                <div className="flex items-start gap-3">
                  <ArrowDownLeft className="mt-1 size-4 text-success-strong" />
                  <div>
                    <p className="text-sm font-medium">
                      Nạp {money(t.amount)}
                    </p>
                    <p className="mt-1 text-xs text-muted">
                      {t.status === "PAID"
                        ? "Đã cộng ví"
                        : t.status === "CANCELLED"
                          ? "Đã hủy"
                          : "Chờ xác minh"}{" "}
                      · {new Date(t.created_at).toLocaleDateString("vi-VN")}
                    </p>
                  </div>
                </div>
                {t.status === "PENDING" && t.checkout_url && (
                  <button
                    onClick={() => setCheckout(checkoutFromTopup(t))}
                    className="rounded-lg border border-line px-3 py-2 text-sm font-medium"
                  >
                    Xem QR
                  </button>
                )}
              </div>
            ))}
            {data?.withdrawals.map((w) => (
              <div
                key={w.id}
                className="flex items-start gap-3 rounded-xl border border-line p-4"
              >
                <ArrowUpRight className="mt-1 size-4 text-muted" />
                <div>
                  <p className="text-sm font-medium">Rút {money(w.amount)}</p>
                  <p className="mt-1 text-xs text-muted">
                    {w.bank_name} · •••• {w.bank_account.slice(-4)} ·{" "}
                    {w.status === "PAID"
                      ? "Đã chuyển"
                      : w.status === "PROCESSING"
                        ? "Đang chuyển"
                        : w.status === "PENDING"
                          ? "Chờ duyệt"
                          : "Đã hoàn về ví"}
                  </p>
                  <p className="mt-1 text-xs text-muted">
                    {new Date(w.created_at).toLocaleDateString("vi-VN")}
                  </p>
                </div>
              </div>
            ))}
            {!data?.topups.length && !data?.withdrawals.length && (
              <p className="py-8 text-center text-sm text-muted">
                Chưa có yêu cầu trên trang này.
              </p>
            )}
          </div>
        )}
        <footer className="flex items-center justify-between gap-3 border-t border-line pt-4 text-sm">
          <button
            disabled={offset === 0 || loading}
            onClick={() => setOffset((n) => Math.max(0, n - 50))}
            className="flex items-center gap-1 rounded-lg border px-3 py-2 disabled:opacity-30"
          >
            <ChevronLeft className="size-4" />
            Trước
          </button>
          <span className="text-xs text-muted">
            Trang {offset / 50 + 1}
          </span>
          <button
            disabled={!hasNext || loading}
            onClick={() => setOffset((n) => n + 50)}
            className="flex items-center gap-1 rounded-lg border px-3 py-2 disabled:opacity-30"
          >
            Sau
            <ChevronRight className="size-4" />
          </button>
        </footer>
      </div>
      {checkout && (
        <TopupCheckoutDialog
          key={checkout.id}
          checkout={checkout}
          status={data?.topups.find((t) => t.id === checkout.id)?.status}
          execute={run}
          onClose={() => {
            setCheckout(null);
            setReload((n) => n + 1);
          }}
        />
      )}
    </PaymentDialog>
  );
}
