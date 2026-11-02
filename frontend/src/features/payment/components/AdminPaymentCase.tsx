"use client";
import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import {
  paymentApi,
  paymentError,
  money,
  type PaymentSession,
} from "../api/paymentApi";
import { SessionPaymentCard } from "./SessionPaymentCard";

export function AdminPaymentCase({ id }: { id: string }) {
  const [session, setSession] = useState<PaymentSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let alive = true;
    const run = async () => {
      try {
        const res = await paymentApi.adminCase(id);
        if (alive) {
          setSession(res.data ?? null);
          setError(null);
        }
      } catch (err) {
        if (alive) setError(paymentError(err));
      }
    };
    void run();
    const refresh = () => {
      if (!document.hidden) void run();
    };
    const timer = setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    return () => {
      alive = false;
      clearInterval(timer);
      window.removeEventListener("focus", refresh);
    };
  }, [id]);
  const value = Number(amount);
  const valid =
    !!session &&
    amount !== "" &&
    Number.isSafeInteger(value) &&
    value >= 0 &&
    value <= session.amount &&
    note.trim().length > 0;
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid || busy) return;
    if (!review) {
      setReview(true);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await paymentApi.resolveCase(id, value, note.trim());
      setSession(res.data ?? null);
      setReview(false);
    } catch (err) {
      setError(paymentError(err));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="mx-auto max-w-4xl space-y-5 pb-8">
      <Link href="/admin/disputes" className="text-sm text-sky-700 underline">
        Quay lại danh sách hồ sơ
      </Link>
      <h1 className="text-2xl font-bold">Hồ sơ học phí buổi học</h1>
      {error && (
        <p
          role="alert"
          className="rounded-lg bg-red-50 p-3 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      {!session && !error && <p>Đang tải hồ sơ…</p>}
      {session && (
        <>
          <SessionPaymentCard
            session={session}
            disabled
            execute={async () => false}
          />
          {["AWAITING_ADMIN", "ADMIN_REVIEW"].includes(session.status) && (
            <form
              onSubmit={submit}
              className="space-y-4 rounded-2xl border border-amber-200 bg-white p-5"
            >
              <h2 className="font-bold">
                Quyết định sau khi xem xét bằng chứng
              </h2>
              <p className="text-sm">
                Số tiền nhập là học phí tính cho gia sư trước phí nền tảng; phần
                còn lại hoàn người trả. Nhập 0 để hoàn toàn bộ.
              </p>
              {session.sessionStatus !== "COMPLETED" && (
                <p className="rounded-lg bg-amber-50 p-3 text-sm">
                  Lịch học chưa ghi nhận hoàn thành. Admin cần đối chiếu bằng
                  chứng trước khi quyết định tính học phí.
                </p>
              )}
              <label className="block text-sm">
                Học phí được chấp nhận
                <input
                  type="number"
                  required
                  min={0}
                  max={session.amount}
                  step={1}
                  value={amount}
                  onChange={(e) => {
                    setAmount(e.target.value);
                    setReview(false);
                  }}
                  className="mt-1 w-full rounded-lg border p-2"
                />
              </label>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setAmount("0");
                    setReview(false);
                  }}
                  className="rounded-lg border px-3 py-2 text-sm"
                >
                  Hoàn toàn bộ
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setAmount(String(session.amount));
                    setReview(false);
                  }}
                  className="rounded-lg border px-3 py-2 text-sm"
                >
                  Tính đủ học phí
                </button>
                {session.learnerAmount !== null &&
                  session.learnerAmount === session.tutorAmount && (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => {
                        setAmount(String(session.learnerAmount));
                        setReview(false);
                      }}
                      className="rounded-lg border px-3 py-2 text-sm"
                    >
                      Dùng mức hai bên thống nhất
                    </button>
                  )}
              </div>
              <label className="block text-sm">
                Lý do quyết định
                <textarea
                  required
                  maxLength={2000}
                  value={note}
                  onChange={(e) => {
                    setNote(e.target.value);
                    setReview(false);
                  }}
                  className="mt-1 w-full rounded-lg border p-2"
                />
              </label>
              {review && valid && (
                <div
                  role="region"
                  aria-label="Xem lại quyết định"
                  className="space-y-2 rounded-lg bg-amber-50 p-4 text-sm"
                >
                  <p>
                    Hoàn ví người trả:{" "}
                    <strong>{money(session.amount - value)}</strong>
                  </p>
                  <p>
                    Gia sư nhận:{" "}
                    <strong>
                      {money(
                        value -
                          Math.floor((value * session.commissionBps) / 10000),
                      )}
                    </strong>
                  </p>
                  <p>
                    Phí nền tảng:{" "}
                    {money(Math.floor((value * session.commissionBps) / 10000))}
                  </p>
                  <p>Quyết định sẽ kết thúc hồ sơ và được gửi đến các bên.</p>
                </div>
              )}
              <button
                disabled={!valid || busy}
                className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-bold disabled:opacity-50"
              >
                {review ? "Xác nhận phân xử" : "Xem lại quyết định"}
              </button>
            </form>
          )}
        </>
      )}
    </main>
  );
}
