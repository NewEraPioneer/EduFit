"use client";
import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, ReceiptText } from "lucide-react";
import {
  chargeLabels,
  money,
  paymentApi,
  paymentError,
  type PaymentSession,
} from "../api/paymentApi";

const PAGE_SIZE = 50;
const STATUS_OPTIONS = Object.entries(chargeLabels) as Array<
  [PaymentSession["status"], string]
>;

const formatTime = (iso: string) =>
  new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });

/** Sổ học phí của mọi buổi học (chỉ đọc), để admin tra cứu khoản đã thu, đã tất toán hoặc đã hoàn. */
export function AdminTuitionLedger() {
  const [status, setStatus] = useState<string>("");
  const [offset, setOffset] = useState(0);
  const [rows, setRows] = useState<PaymentSession[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    paymentApi
      .charges(status, offset)
      .then((res) => {
        if (!alive) return;
        setRows(res.data ?? []);
        setError(null);
      })
      .catch((e) => {
        if (alive) setError(paymentError(e));
      });
    return () => {
      alive = false;
    };
  }, [status, offset]);

  const button =
    "rounded-lg border border-line bg-surface px-3 py-2 text-xs font-medium text-ink-2 hover:bg-paper disabled:opacity-40";

  return (
    <section className="overflow-hidden rounded-2xl border border-line bg-surface">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-5">
        <h2 className="font-medium">Học phí các buổi học</h2>
        <select
          aria-label="Lọc theo trạng thái học phí"
          value={status}
          onChange={(e) => {
            setRows(null);
            setOffset(0);
            setStatus(e.target.value);
          }}
          className="rounded-lg border border-line bg-surface px-3 py-2 text-xs font-medium text-ink-2"
        >
          <option value="">Mọi trạng thái</option>
          {STATUS_OPTIONS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </header>

      {error && (
        <p role="alert" className="p-5 text-sm text-danger-strong">
          {error}
        </p>
      )}

      {!error && rows === null && (
        <p role="status" className="p-8 text-center text-sm text-muted">
          Đang tải…
        </p>
      )}

      {rows && rows.length === 0 && (
        <div className="py-12 text-center">
          <ReceiptText className="mx-auto size-9 text-muted" />
          <p className="mt-3 text-sm font-medium">Chưa có khoản học phí nào</p>
        </div>
      )}

      {rows && rows.length > 0 && (
        <div className="divide-y divide-line">
          {rows.map((s) => (
            <article
              key={s.sessionId}
              className="flex flex-wrap items-center justify-between gap-3 p-5"
            >
              <div className="min-w-0">
                <p className="text-sm font-medium">
                  {s.tutorName} · {money(s.amount)}
                </p>
                <p className="mt-1 text-xs text-muted">
                  Buổi học {formatTime(s.startAt)}
                </p>
              </div>
              <div className="text-right">
                <span className="rounded-full border border-line bg-paper px-2.5 py-1 text-xs font-medium text-ink-2">
                  {chargeLabels[s.status] ?? s.status}
                </span>
                {s.status === "SETTLED" && (
                  <p className="mt-1 text-xs text-muted">
                    Đã thanh toán {money(s.settledAmount)}
                  </p>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <footer className="flex items-center justify-between gap-3 border-t border-line p-4">
        <button
          disabled={offset === 0}
          className={button}
          onClick={() => setOffset((n) => Math.max(0, n - PAGE_SIZE))}
        >
          <ChevronLeft className="inline size-4" />
          Trước
        </button>
        <span className="text-xs text-muted">Trang {offset / PAGE_SIZE + 1}</span>
        <button
          disabled={!rows || rows.length < PAGE_SIZE}
          className={button}
          onClick={() => setOffset((n) => n + PAGE_SIZE)}
        >
          Sau
          <ChevronRight className="inline size-4" />
        </button>
      </footer>
    </section>
  );
}
