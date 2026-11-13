"use client";

import React, { useEffect, useId, useState } from "react";
import Link from "next/link";
import { Sparkles, ArrowRight } from "lucide-react";
import type { TutoringClass } from "@/features/connection/types";
import { MODE_LABELS } from "@/features/learning-needs/catalogRules";
import { needTitle } from "@/features/learning-needs/components/LearningNeedSummary";
import { progressApi, type PlanResponse } from "@/features/progress";
import { ApiError } from "@/lib/api";
import type { LearningNeed } from "@/services/learningNeeds";

interface StudentGoalProgressCardProps {
  tutoringClass: TutoringClass;
  need?: LearningNeed;
  nextSessionText?: string;
}

export function StudentGoalProgressCard({ tutoringClass, need, nextSessionText }: StudentGoalProgressCardProps) {
  const titleId = useId();
  const { classId } = tutoringClass;
  const [plan, setPlan] = useState<PlanResponse | "loading" | "error" | null>("loading");

  useEffect(() => {
    let ignore = false;
    async function loadPlan() {
      try {
        const response = await progressApi.getPlanByClassId(classId);
        if (!ignore) {
          setPlan(response.success && response.data?.classId === classId ? response.data : "error");
        }
      } catch (error) {
        if (!ignore) setPlan(error instanceof ApiError && error.status === 404 ? null : "error");
      }
    }
    void loadPlan();
    return () => {
      ignore = true;
    };
  }, [classId]);

  const planData = typeof plan === "object" ? plan : null;
  const milestones = planData
    ? planData.milestones.filter((milestone) => !milestone.isArchived).sort((a, b) => a.sortOrder - b.sortOrder)
    : [];
  const hasMilestones = milestones.length > 0;
  const steps = hasMilestones
    ? milestones.map((milestone) => ({
        id: milestone.milestoneId,
        label: milestone.title,
        completed: milestone.status === "ACHIEVED",
      }))
    : [
        { id: "need", label: "Thêm nhu cầu học", completed: Boolean(tutoringClass.needId) },
        { id: "class", label: "Ghép nối gia sư", completed: true },
        { id: "session", label: "Hoàn thành buổi học đầu", completed: (tutoringClass.completedCount ?? 0) > 0 },
      ];
  const completed = steps.filter((step) => step.completed).length;
  const remaining = steps.length - completed;
  const unit = hasMilestones ? "cột mốc" : "bước";
  const progress = hasMilestones ? planData?.progressPercentage : null;
  const grade = tutoringClass.educationLevelName || need?.educationLevelName;
  const title = (need && needTitle(need)) || tutoringClass.subjectName || "Lớp học kèm";

  return (
    <article aria-labelledby={titleId} className="min-w-0 wrap-anywhere bg-surface p-6 sm:p-7 rounded-3xl border border-line/80 shadow-xs flex flex-col gap-5 card-lift">
      <div>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-sunken rounded-full text-xs font-medium text-ink-2">
            <Sparkles className="size-3.5 text-action-strong" />
            <span>Mục tiêu lớp đang học</span>
          </div>
          {grade && (
            <span className="px-2.5 py-0.5 bg-action-line text-action-strong rounded-full text-xs font-medium">
              {grade}
            </span>
          )}
        </div>

        <h3 id={titleId} className="font-heading text-lg sm:text-xl font-bold text-ink mb-1">
          {title}
        </h3>
        <p className="text-xs text-muted font-medium font-sans">
          {tutoringClass.tutorDisplayName || "Gia sư"}
          {need?.mode && ` · Hình thức: ${MODE_LABELS[need.mode]}`}
        </p>
      </div>

      {plan === "loading" || plan === "error" ? (
        <p role="status" className="text-xs text-muted">
          {plan === "loading" ? "Đang tải lộ trình…" : "Chưa thể tải lộ trình của lớp này."}
        </p>
      ) : (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs font-medium">
              <span className="text-ink-2">
                {hasMilestones ? "Tiến độ theo lộ trình của gia sư" : "Các bước khởi đầu"}
              </span>
              {progress != null && (
                <span className="text-success-strong font-medium text-sm">{progress}%</span>
              )}
            </div>
            {progress != null && (
              <div
                role="progressbar"
                aria-label={title}
                aria-valuenow={progress}
                className="h-2.5 w-full overflow-hidden rounded-full bg-sunken"
              >
                <div
                  className="h-full rounded-full bg-success motion-safe:transition-all motion-safe:duration-700"
                  style={{ width: `${progress}%` }}
                />
              </div>
            )}
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted font-medium">
              <span>{completed} / {steps.length} {unit} đã hoàn thành</span>
              <span>
                {remaining > 0
                  ? `Còn ${remaining} ${unit}`
                  : hasMilestones
                    ? "Đã đạt toàn bộ cột mốc"
                    : "Chờ gia sư lập lộ trình học"}
              </span>
            </div>
          </div>

          <div className="pt-2 border-t border-line grid grid-cols-1 sm:grid-cols-3 gap-2">
            {steps.slice(0, 6).map((m, idx) => (
              <div
                key={m.id}
                className={`p-2 rounded-xl text-xs flex items-center gap-1.5 ${
                  m.completed
                    ? "bg-success-soft text-success-strong font-medium"
                    : "bg-paper text-muted"
                }`}
              >
                <span
                  className={`size-4 rounded-full flex items-center justify-center text-xs shrink-0 font-medium ${
                    m.completed
                      ? "bg-success text-white"
                      : "bg-line text-ink-2"
                  }`}
                >
                  {m.completed ? "✓" : idx + 1}
                </span>
                <span className="min-w-0 wrap-anywhere leading-snug">{m.label}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3 text-xs">
        <span className="text-muted font-medium">
          {nextSessionText || "Chưa có buổi học tiếp theo"}
        </span>
        <Link
          href={{ pathname: "/student/progress", query: { classId } }}
          className="text-action-strong hover:text-action-strong font-medium flex items-center gap-1 transition-transform hover:translate-x-0.5"
        >
          Xem chi tiết lộ trình <ArrowRight className="size-3.5 shrink-0" />
        </Link>
      </div>
    </article>
  );
}
