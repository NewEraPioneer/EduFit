"use client";
import Link from "next/link";
import { useState } from "react";
import { ArrowRight, CalendarDays } from "lucide-react";
import { money, type PaymentSession } from "../api/paymentApi";
import { paymentStatus, sessionPaymentHref } from "../paymentPresentation";
export function PaymentSessionsPanel({
  sessions,
  role,
  month,
  onMonth,
}: {
  sessions: PaymentSession[];
  role: "STUDENT" | "PARENT" | "TUTOR";
  month: string;
  onMonth: (value: string) => void;
}) {
  const [filter, setFilter] = useState("all");
  const [limit, setLimit] = useState(5);
  const done = (s: PaymentSession) =>
    ["SETTLED", "REFUNDED", "EXPIRED"].includes(s.status);
  const needs = (s: PaymentSession) =>
    ["AWAITING_CONFIRMATION", "DISPUTED"].includes(s.status) || s.canFund;
  const visible = sessions.filter((s) =>
    filter === "done" ? done(s) : filter === "action" ? needs(s) : true,
  );
  return (
    <section className="min-w-0 rounded-2xl border border-stone-200 bg-white">
      <header className="space-y-4 border-b border-stone-100 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="flex items-center gap-2 font-bold">
            <CalendarDays className="size-4 text-neutral-500" />
            {role === "TUTOR" ? "Học phí buổi dạy" : "Học phí buổi học"}
          </h2>
          <input
            aria-label="Tháng học phí"
            type="month"
            value={month}
            onChange={(e) => {
              if (e.target.value) onMonth(e.target.value);
            }}
            className="min-w-0 rounded-lg border border-stone-200 bg-white px-2 py-2 text-xs text-neutral-600"
          />
        </div>
        <div className="flex flex-wrap gap-2">
          {(
            [
              ["all", "Tất cả", sessions.length],
              ["action", "Cần phản hồi", sessions.filter(needs).length],
              ["done", "Đã xong", sessions.filter(done).length],
            ] as const
          ).map(([key, label, count]) => (
            <button
              key={key}
              aria-pressed={filter === key}
              onClick={() => {
                setFilter(key);
                setLimit(5);
              }}
              className={`rounded-full px-3 py-2 text-xs font-semibold ${
                filter === key
                  ? "bg-stone-900 text-white"
                  : "bg-stone-100 text-neutral-600 hover:bg-stone-200"
              }`}
            >
              {label} <span className="ml-1 opacity-70">{count}</span>
            </button>
          ))}
        </div>
      </header>
      {visible.length ? (
        <ul className="divide-y divide-stone-100">
          {visible.slice(0, limit).map((s) => (
            <li
              key={s.sessionId}
              className="flex flex-wrap items-center gap-3 p-5"
            >
              <div className="flex size-12 shrink-0 flex-col items-center justify-center rounded-xl bg-stone-50">
                <span className="text-lg font-bold leading-tight">
                  {new Date(s.startAt).getDate()}
                </span>
                <span className="text-[10px] uppercase text-neutral-500">
                  Tháng {new Date(s.startAt).getMonth() + 1}
                </span>
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold">
                  {role === "TUTOR" ? "Buổi dạy" : s.tutorName}
                </p>
                <p className="mt-1 text-xs text-neutral-500">
                  {new Date(s.startAt).toLocaleTimeString("vi-VN", {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}{" "}
                  · {paymentStatus(s)}
                </p>
                {s.reviewDeadline && !done(s) && (
                  <p className="mt-1 text-xs text-amber-800">
                    Phản hồi trước{" "}
                    {new Date(s.reviewDeadline).toLocaleString("vi-VN", {
                      day: "2-digit",
                      month: "2-digit",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-4">
                <strong className="text-sm">{money(s.amount)}</strong>
                <Link
                  aria-label={`Chi tiết buổi học ${s.sessionId}`}
                  href={sessionPaymentHref(role, s.sessionId)}
                  className="rounded-full border border-stone-200 p-2 text-neutral-600 hover:bg-amber-50"
                >
                  <ArrowRight className="size-4" />
                </Link>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <div className="px-5 py-12 text-center">
          <CalendarDays className="mx-auto size-8 text-stone-300" />
          <p className="mt-3 text-sm font-semibold">
            {filter === "action"
              ? "Không có buổi cần phản hồi"
              : "Chưa có buổi học phù hợp"}
          </p>
          <p className="mt-1 text-xs text-neutral-500">
            Bạn có thể chọn tháng hoặc bộ lọc khác.
          </p>
        </div>
      )}
      {visible.length > limit && (
        <div className="border-t border-stone-100 p-4 text-center">
          <button
            onClick={() => setLimit((n) => n + 5)}
            className="text-sm font-semibold text-amber-800"
          >
            Xem thêm {Math.min(5, visible.length - limit)} buổi
          </button>
        </div>
      )}
    </section>
  );
}
