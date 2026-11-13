"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  paymentApi,
  paymentError,
  type PaymentSession,
  type LearningAccess,
} from "../api/paymentApi";
import { SessionPaymentCard } from "./SessionPaymentCard";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
export function SessionBillingPanel({
  sessionId,
  role,
  mode,
}: {
  sessionId: string;
  role: "STUDENT" | "PARENT" | "TUTOR";
  mode?: string;
}) {
  const [session, setSession] = useState<PaymentSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [learning, setLearning] = useState<LearningAccess | null>(null);
  const [meetingLink, setMeetingLink] = useState("");
  const load = useCallback(async () => {
    const result = await paymentApi.session(sessionId);
    setSession(result.data ?? null);
    if (result.data?.sessionStatus === "SCHEDULED") {
      const readiness = await paymentApi.learningAccess(sessionId);
      setLearning(readiness.data ?? null);
    } else setLearning(null);
  }, [sessionId]);
  useEffect(() => {
    let alive = true;
    const run = async () => {
      try {
        const result = await paymentApi.session(sessionId);
        if (alive) {
          setSession(result.data ?? null);
          setError(null);
          if (result.data?.sessionStatus === "SCHEDULED") {
            const readiness = await paymentApi.learningAccess(sessionId);
            if (alive) setLearning(readiness.data ?? null);
          } else setLearning(null);
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
        <h3 className="text-sm font-medium">Thanh toán buổi học</h3>
        <Link className="text-xs text-brand-strong underline" href={href}>
          Mở ví & lịch sử
        </Link>
      </div>
      {error && (
        <p role="alert" className="text-sm text-danger-strong">
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
          <p className="text-sm text-muted">Đang kiểm tra học phí…</p>
        )
      )}
      {session?.sessionStatus === "SCHEDULED" && learning && (
        <div className="space-y-2 rounded-xl border border-sky-200 bg-sky-50 p-4 text-sm text-sky-950">
          <p>{learning.message}</p>
          {learning.startedAt && <p className="text-xs">Đã ghi nhận bắt đầu lúc {new Date(learning.startedAt).toLocaleTimeString("vi-VN")}</p>}
          {learning.canStart && !learning.startedAt && (
            <button type="button" disabled={busy} className="rounded-lg bg-sky-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
              onClick={() => void execute(async () => {
                const result = await paymentApi.startLesson(sessionId);
                if (result.data) setLearning(result.data);
              })}>
              {busy ? "Đang xử lý…" : "Bắt đầu buổi học"}
            </button>
          )}
          {learning.canStart && learning.meetingLink && (
            <a className="inline-block rounded-lg bg-sky-700 px-4 py-2 font-semibold text-white" href={learning.meetingLink} target="_blank" rel="noopener noreferrer">Vào phòng học</a>
          )}
        </div>
      )}
      {role === "TUTOR" && mode === "ONLINE" && session?.sessionStatus === "SCHEDULED" && (
        <details className="rounded-xl border border-stone-200 p-3 text-sm">
          <summary className="cursor-pointer font-semibold">Cập nhật phòng học trực tuyến</summary>
          <form className="mt-3 flex flex-wrap gap-2" onSubmit={(event) => {
            event.preventDefault();
            void execute(() => schedulingApi.updateMeetingLink(sessionId, meetingLink.trim()));
          }}>
            <label className="w-full text-xs" htmlFor={`meeting-link-${sessionId}`}>Link phòng học (http/https)</label>
            <input id={`meeting-link-${sessionId}`} type="url" required maxLength={255} value={meetingLink}
              onChange={(event) => setMeetingLink(event.target.value)} placeholder="https://meet.google.com/..."
              className="min-w-0 flex-1 rounded-lg border border-stone-300 px-3 py-2" />
            <button type="submit" disabled={busy} className="rounded-lg border px-3 py-2 font-semibold disabled:opacity-50">Lưu link</button>
          </form>
        </details>
      )}
    </section>
  );
}
