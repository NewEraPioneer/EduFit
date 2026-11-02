"use client";
import { useState, type FormEvent } from "react";
import { PaymentDialog } from "./PaymentDialog";
export interface AdminReviewTarget {
  kind: "topups" | "withdrawals";
  id: string;
  approve: boolean;
  label: string;
  summary: string;
  payout?: boolean;
}
export function AdminPaymentReviewDialog({
  target,
  busy,
  error,
  onClose,
  onConfirm,
}: {
  target: AdminReviewTarget;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onConfirm: (note: string, reference: string) => Promise<void>;
}) {
  const [note, setNote] = useState("");
  const [reference, setReference] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!busy) await onConfirm(note, reference);
  }
  return (
    <PaymentDialog title={target.label} onClose={onClose} busy={busy}>
      <form onSubmit={submit} className="space-y-5">
        <p className="rounded-xl bg-stone-50 p-4 text-sm leading-relaxed text-neutral-600">
          {target.summary}
        </p>
        {error && (
          <p
            role="alert"
            className="rounded-xl bg-red-50 p-3 text-sm text-red-700"
          >
            {error}
          </p>
        )}
        <fieldset disabled={busy} className="space-y-4">
          <label className="block text-sm font-medium">
            Ghi chú / lý do
            <textarea
              required
              maxLength={2000}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={3}
              className="mt-2 w-full rounded-xl border border-stone-300 p-3"
            />
          </label>
          {target.approve && !target.payout && (
            <label className="block text-sm font-medium">
              Mã giao dịch ngân hàng đã xác minh
              <input
                required
                minLength={6}
                maxLength={200}
                value={reference}
                onChange={(e) => setReference(e.target.value)}
                className="mt-2 w-full rounded-xl border border-stone-300 p-3"
              />
              <span className="mt-2 block text-xs font-normal text-neutral-500">
                Chỉ xác nhận sau khi đã đối chiếu giao dịch thật.
              </span>
            </label>
          )}
        </fieldset>
        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            className="rounded-xl border border-stone-200 px-3 py-3 text-sm font-semibold disabled:opacity-50"
          >
            Hủy
          </button>
          <button
            disabled={busy}
            className="rounded-xl bg-amber-500 px-3 py-3 text-sm font-bold disabled:opacity-50"
          >
            {busy ? "Đang xử lý…" : "Xác nhận xử lý"}
          </button>
        </div>
        <details className="text-xs text-neutral-500">
          <summary className="cursor-pointer">Mã tham chiếu nội bộ</summary>
          <p className="mt-2 break-all">{target.id}</p>
        </details>
      </form>
    </PaymentDialog>
  );
}
