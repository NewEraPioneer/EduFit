"use client";
import { AlertCircle, ArrowUpRight } from "lucide-react";
import { money, type PaymentSession } from "../api/paymentApi";

/** Số tiền nạp tối thiểu (khớp giới hạn của ô nạp tiền). */
export const MIN_TOPUP = 10000;

/**
 * Các buổi học đến hạn mà hệ thống chưa thu được học phí. Người học chỉ cần nạp đủ phần còn
 * thiếu; hệ thống tự thu, buổi cũ nhất trước.
 */
export function TuitionDuePanel({
  due,
  available,
  busy,
  canTopup,
  onTopup,
}: {
  due: PaymentSession[];
  available: number;
  busy: boolean;
  canTopup: boolean;
  onTopup: (amount: number) => void;
}) {
  if (!due.length) return null;
  const total = due.reduce((sum, s) => sum + s.amount, 0);
  const shortfall = Math.max(0, total - available);
  const sorted = [...due].sort(
    (a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime(),
  );
  return (
    <section
      aria-label="Cần thanh toán"
      className="space-y-4 rounded-xl border border-danger-line bg-danger-soft p-4 text-ink"
    >
      <div className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger-strong" />
        <div className="min-w-0">
          <h3 className="text-sm font-bold text-danger-strong">
            Cần thanh toán
          </h3>
          <p className="mt-1 text-xs leading-relaxed text-ink-2">
            {due.length} buổi học đến hạn, tổng {money(total)}. Hệ thống tự thu
            khi ví đủ tiền, buổi cũ nhất trước.
          </p>
        </div>
      </div>
      <ul className="space-y-2">
        {sorted.map((s) => (
          <li
            key={s.sessionId}
            className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-sm"
          >
            <span className="min-w-0">
              {new Date(s.startAt).toLocaleString("vi-VN", {
                day: "2-digit",
                month: "2-digit",
                hour: "2-digit",
                minute: "2-digit",
              })}{" "}
              · {s.tutorName}
              <span className="ml-1 text-xs text-muted">
                {s.sessionStatus === "COMPLETED"
                  ? "(đã học)"
                  : "(trước buổi học)"}
              </span>
            </span>
            <strong>{money(s.amount)}</strong>
          </li>
        ))}
      </ul>
      {shortfall > 0 ? (
        <button
          type="button"
          disabled={busy || !canTopup}
          onClick={() => onTopup(Math.max(MIN_TOPUP, shortfall))}
          className="flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-action px-4 py-3 text-sm font-medium text-on-action hover:bg-action-deep disabled:cursor-not-allowed disabled:bg-line disabled:text-muted"
        >
          Nạp {money(Math.max(MIN_TOPUP, shortfall))}
          <ArrowUpRight className="size-4" />
        </button>
      ) : (
        <p role="status" className="text-xs text-success-strong">
          Ví đã đủ tiền. Hệ thống sẽ tự thu trong ít phút.
        </p>
      )}
    </section>
  );
}
