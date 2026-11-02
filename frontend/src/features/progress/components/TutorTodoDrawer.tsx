"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { ClipboardList, Loader2, X } from "lucide-react";
import { progressApi } from "../api/progressApi";
import type { PlanResponse, TutorTodos } from "../types";
import { SessionOutcomeModal } from "./SessionOutcomeModal";

/** Nơi gia sư lập/sửa lộ trình của lớp. */
export const TUTOR_CLASSES_HREF = "/tutor/classes";

const formatDateTime = (iso: string) => {
  try {
    return new Intl.DateTimeFormat("vi-VN", {
      weekday: "short",
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
};

const formatDate = (isoDate: string) => {
  const [y, m, d] = isoDate.split("-");
  return d && m && y ? `${d}/${m}/${y}` : isoDate;
};

const countTodos = (t: TutorTodos | null) =>
  t
    ? t.sessionsMissingFeedback.length +
      t.classesWithoutPlan.length +
      t.overdueMilestones.length +
      t.plansMissingFinalDate.length
    : 0;

type FeedbackTarget = TutorTodos["sessionsMissingFeedback"][number];

/**
 * Ngăn "Việc cần làm" của gia sư (mục 3.14): buổi chưa phản hồi (bổ sung ngay trong ngăn), lớp chưa có lộ trình,
 * cột mốc quá hạn, lộ trình thiếu ngày cuối. Mọi danh sách do backend tính.
 */
export function TutorTodoDrawer() {
  const [todos, setTodos] = useState<TutorTodos | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedbackTarget, setFeedbackTarget] = useState<FeedbackTarget | null>(null);
  const [feedbackPlan, setFeedbackPlan] = useState<PlanResponse | null>(null);

  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    progressApi
      .getTutorTodos()
      .then((res) => {
        if (ignore) return;
        if (res.success && res.data) setTodos(res.data);
        else setError(res.message || "Không tải được việc cần làm");
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof Error ? err.message : "Không tải được việc cần làm");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const openFeedback = async (item: FeedbackTarget) => {
    setError(null);
    try {
      const res = await progressApi.getPlanByClassId(item.classId);
      if (!res.success || !res.data) {
        setError(res.message || "Không tải được lộ trình của lớp");
        return;
      }
      setFeedbackPlan(res.data);
      setFeedbackTarget(item);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không tải được lộ trình của lớp");
    }
  };

  const total = countTodos(todos);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-amber-50 hover:bg-amber-100 border border-amber-200 text-amber-900 rounded-xl text-xs font-bold shadow-2xs transition-colors shrink-0 cursor-pointer"
      >
        <ClipboardList className="size-3.5" />
        <span>Việc cần làm</span>
        <span
          className="rounded-full bg-amber-500 px-1.5 text-[10px] text-white"
          aria-label={`${total} việc cần làm`}
          data-testid="tutor-todo-count"
        >
          {loading ? "…" : total}
        </span>
      </button>

      {open && (
        <div role="dialog" aria-modal="true" aria-labelledby="tutor-todo-title" className="fixed inset-0 z-40 flex justify-end bg-stone-900/40">
          <div className="h-full w-full max-w-md overflow-y-auto bg-white shadow-2xl p-5 space-y-5">
            <div className="flex items-center justify-between">
              <h2 id="tutor-todo-title" className="font-heading text-base font-bold text-neutral-900">
                Việc cần làm
              </h2>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Đóng"
                className="grid size-8 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 cursor-pointer"
              >
                <X className="size-4" />
              </button>
            </div>

            {error && (
              <p role="alert" className="rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs text-rose-800">
                {error}
              </p>
            )}
            {loading && (
              <p className="flex items-center gap-2 text-xs text-neutral-500">
                <Loader2 className="size-3.5 animate-spin" /> Đang tải…
              </p>
            )}
            {!loading && todos && total === 0 && (
              <p className="rounded-2xl bg-emerald-50 border border-emerald-200 p-4 text-xs text-emerald-900">
                Không còn việc nào cần làm. Mọi buổi học đã có phản hồi và lộ trình đều đúng hạn.
              </p>
            )}

            {todos && todos.sessionsMissingFeedback.length > 0 && (
              <TodoSection title="Buổi học chưa phản hồi" count={todos.sessionsMissingFeedback.length}>
                {todos.sessionsMissingFeedback.map((s) => (
                  <li key={s.sessionId} className="flex items-center justify-between gap-2">
                    <span>
                      <strong>{s.studentName ?? "Học sinh"}</strong> · {formatDateTime(s.startAt)}
                      {s.late && (
                        <span className="ml-1 rounded-full bg-stone-200 px-1.5 py-0.5 text-[10px] font-bold text-neutral-700">
                          Bổ sung muộn
                        </span>
                      )}
                    </span>
                    <button
                      type="button"
                      onClick={() => void openFeedback(s)}
                      className="shrink-0 min-h-8 px-2.5 rounded-lg bg-emerald-600 text-white text-[11px] font-bold cursor-pointer"
                    >
                      Phản hồi
                    </button>
                  </li>
                ))}
              </TodoSection>
            )}

            {todos && todos.classesWithoutPlan.length > 0 && (
              <TodoSection title="Lớp chưa có lộ trình" count={todos.classesWithoutPlan.length}>
                {todos.classesWithoutPlan.map((c) => (
                  <li key={c.classId} className="flex items-center justify-between gap-2">
                    <span>
                      <strong>{c.studentName ?? "Học sinh"}</strong> · {c.classTitle}
                    </span>
                    <Link href={TUTOR_CLASSES_HREF} className="shrink-0 text-[11px] font-bold text-amber-700 underline">
                      Lập lộ trình
                    </Link>
                  </li>
                ))}
              </TodoSection>
            )}

            {todos && todos.overdueMilestones.length > 0 && (
              <TodoSection title="Cột mốc quá hạn" count={todos.overdueMilestones.length}>
                {todos.overdueMilestones.map((m) => (
                  <li key={m.milestoneId}>
                    <strong>{m.milestoneTitle}</strong> ({m.studentName ?? "Học sinh"}) · hạn {formatDate(m.targetDate)}, trễ{" "}
                    {m.daysLate} ngày
                  </li>
                ))}
              </TodoSection>
            )}

            {todos && todos.plansMissingFinalDate.length > 0 && (
              <TodoSection title="Lộ trình thiếu ngày cuối" count={todos.plansMissingFinalDate.length}>
                {todos.plansMissingFinalDate.map((c) => (
                  <li key={c.classId} className="flex items-center justify-between gap-2">
                    <span>
                      <strong>{c.studentName ?? "Học sinh"}</strong> · {c.classTitle}
                    </span>
                    <Link href={TUTOR_CLASSES_HREF} className="shrink-0 text-[11px] font-bold text-amber-700 underline">
                      Đặt hạn cuối
                    </Link>
                  </li>
                ))}
              </TodoSection>
            )}
          </div>
        </div>
      )}

      {feedbackTarget && (
        <SessionOutcomeModal
          isOpen
          outcomeAlreadyRecorded
          onClose={() => setFeedbackTarget(null)}
          session={{
            sessionId: feedbackTarget.sessionId,
            classId: feedbackTarget.classId,
            startAt: feedbackTarget.startAt,
            endAt: feedbackTarget.endAt,
            studentName: feedbackTarget.studentName ?? undefined,
          }}
          plan={feedbackPlan}
          onSuccess={() => {
            setFeedbackTarget(null);
            setReloadKey((k) => k + 1);
          }}
        />
      )}
    </>
  );
}

function TodoSection({ title, count, children }: { title: string; count: number; children: React.ReactNode }) {
  return (
    <section className="space-y-2" aria-label={title}>
      <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-600">
        {title} <span className="text-neutral-400">({count})</span>
      </h3>
      <ul className="space-y-2 text-xs text-neutral-800">{children}</ul>
    </section>
  );
}
