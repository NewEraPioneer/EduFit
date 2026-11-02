"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  paymentApi,
  paymentError,
  type PaymentSession,
} from "../api/paymentApi";
import { SessionPaymentCard } from "./SessionPaymentCard";
export function SessionBillingPanel({
  sessionId,
  role,
}: {
  sessionId: string;
  role: "STUDENT" | "PARENT" | "TUTOR";
}) {
  const [session, setSession] = useState<PaymentSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    const result = await paymentApi.session(sessionId);
    setSession(result.data ?? null);
  }, [sessionId]);
  useEffect(() => {
    let alive = true;
    const run = async () => {
      try {
        const result = await paymentApi.session(sessionId);
        if (alive) {
          setSession(result.data ?? null);
          setError(null);
        }
      } catch (e) {
        if (alive) setError(paymentError(e));
      }
    };
    void run();
    const timer = setInterval(() => {
      if (!document.hidden) void run();
    }, 15000);
    const focus = () => void run();
    window.addEventListener("focus", focus);
    return () => {
      alive = false;
      clearInterval(timer);
      window.removeEventListener("focus", focus);
    };
  }, [sessionId]);
  async function execute(work: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await work();
      await load();
      return true;
    } catch (e) {
      setError(paymentError(e));
      return false;
    } finally {
      setBusy(false);
    }
  }
  const href =
    role === "STUDENT"
      ? "/student/payments"
      : role === "PARENT"
        ? "/parent/billing"
        : "/tutor/earnings";
  return (
    <section className="space-y-2">
      <div className="flex justify-between gap-2">
        <h3 className="text-sm font-bold">Thanh toán buổi học</h3>
        <Link className="text-xs text-sky-700 underline" href={href}>
          Mở ví & lịch sử
        </Link>
      </div>
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}
      {session ? (
        <SessionPaymentCard
          session={session}
          disabled={busy}
          execute={execute}
        />
      ) : (
        !error && (
          <p className="text-sm text-neutral-500">Đang kiểm tra học phí…</p>
        )
      )}
    </section>
  );
}
