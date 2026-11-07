"use client";

import React, { useEffect, useMemo, useState } from "react";
import { BookOpen, Loader2 } from "lucide-react";
import { progressApi } from "../api/progressApi";
import type { PlanResponse, SessionMilestones } from "../types";

/** Khớp backend SessionMilestonePlanningService.MAX_PLANNED_MILESTONES. */
export const MAX_PLANNED_MILESTONES = 3;

export interface PlannedMilestonesPickerProps {
  sessionId: string;
  plan?: PlanResponse | null;
  /** Gia sư được sửa khi buổi chưa diễn ra; người khác chỉ xem. */
  editable: boolean;
  /** Báo cho nơi dùng (ví dụ biểu mẫu phản hồi) cột mốc dự kiến hiện tại. */
  onChange?: (milestoneIds: string[]) => void;
}

/**
 * Cột mốc dự kiến của một buổi học (tùy chọn, tối đa {@link MAX_PLANNED_MILESTONES}).
 * Buổi đã phản hồi thì hiển thị cột mốc thực tế (thực tế thắng dự kiến) và không sửa được.
 */
export function PlannedMilestonesPicker({ sessionId, plan, editable, onChange }: PlannedMilestonesPickerProps) {
  const options = useMemo(
    () =>
      (plan?.milestones ?? [])
        .filter((m) => !m.isArchived && m.status !== "ACHIEVED")
        .sort((a, b) => a.sortOrder - b.sortOrder),
    [plan]
  );
  const [current, setCurrent] = useState<SessionMilestones | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);

  useEffect(() => {
    let ignore = false;
    progressApi
      .getSessionMilestones([sessionId])
      .then((res) => {
        if (ignore || !res.success) return;
        const found = res.data?.find((m) => m.sessionId === sessionId) ?? null;
        setCurrent(found);
        const ids = found?.linkType === "PLANNED" ? found.milestones.map((m) => m.milestoneId) : [];
        setSelected(ids);
        onChange?.(ids);
      })
      .catch(() => {});
    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  if (current?.linkType === "ACTUAL") {
    return (
      <p className="flex items-start gap-1.5 text-xs text-neutral-700" data-testid="session-actual-milestones">
        <BookOpen className="size-3.5 text-amber-600 shrink-0 mt-px" />
        Đã học: {current.milestones.map((m) => m.title).join(" · ")}
      </p>
    );
  }

  if (!editable) {
    if (!current) return null;
    return (
      <p className="flex items-start gap-1.5 text-xs text-neutral-700" data-testid="session-planned-milestones">
        <BookOpen className="size-3.5 text-amber-600 shrink-0 mt-px" />
        Dự kiến học: {current.milestones.map((m) => m.title).join(" · ")}
      </p>
    );
  }

  if (options.length === 0) return null;

  const toggle = (id: string) => {
    setMessage(null);
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : prev.length < MAX_PLANNED_MILESTONES ? [...prev, id] : prev
    );
  };

  const save = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const res = await progressApi.planSessionMilestones(sessionId, selected);
      if (res.success) {
        setCurrent(res.data && res.data.milestones.length > 0 ? res.data : null);
        onChange?.(selected);
        setMessage({ tone: "ok", text: "Đã lưu cột mốc dự kiến" });
      } else {
        setMessage({ tone: "error", text: res.message || "Chưa lưu được cột mốc dự kiến" });
      }
    } catch (err: unknown) {
      setMessage({ tone: "error", text: err instanceof Error ? err.message : "Chưa lưu được cột mốc dự kiến" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2" aria-labelledby={`planned-heading-${sessionId}`}>
      <h4 id={`planned-heading-${sessionId}`} className="text-xs font-bold text-neutral-800">
        Dự kiến học <span className="font-normal text-neutral-400">(tùy chọn, tối đa {MAX_PLANNED_MILESTONES} cột mốc)</span>
      </h4>
      <div className="flex flex-wrap gap-1.5">
        {options.map((m) => (
          <button
            key={m.milestoneId}
            type="button"
            aria-pressed={selected.includes(m.milestoneId)}
            onClick={() => toggle(m.milestoneId)}
            className={`min-h-9 rounded-full border px-3 text-xs font-semibold cursor-pointer ${
              selected.includes(m.milestoneId)
                ? "border-amber-500 bg-amber-50 text-amber-900"
                : "border-stone-200 bg-white text-neutral-600"
            }`}
          >
            {m.title}
          </button>
        ))}
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={save}
          disabled={saving}
          className="min-h-9 px-3 rounded-xl border border-amber-300 bg-amber-50 text-amber-900 text-xs font-bold flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
        >
          {saving && <Loader2 className="size-3.5 animate-spin" />}
          Lưu dự kiến
        </button>
        {message && (
          <span role={message.tone === "error" ? "alert" : "status"} className={`text-[11px] ${message.tone === "error" ? "text-rose-700" : "text-emerald-700"}`}>
            {message.text}
          </span>
        )}
      </div>
    </section>
  );
}
