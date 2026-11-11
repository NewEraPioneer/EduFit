"use client";

import React, { useEffect, useMemo, useState } from "react";
import { AlertCircle, CheckCircle2, Clock, FileText, Loader2, User, X, XCircle } from "lucide-react";
import { progressApi } from "../api/progressApi";
import { recordSessionOutcomeSchema, sessionFeedbackSchema } from "../schemas/progressSchemas";
import {
  ATTITUDE_CHIPS,
  EVIDENCE_NOTE_MAX_LENGTH,
  FEEDBACK_COMMENT_MAX_LENGTH,
  HOMEWORK_OPTIONS,
  INDEPENDENCE_LEVELS,
  PARENT_MESSAGE_MAX_LENGTH,
} from "../constants/feedbackLabels";
import type {
  AttitudeChip,
  HomeworkStatus,
  IndependenceLevel,
  MilestoneResponse,
  PlanResponse,
  SubmitSessionFeedbackPayload,
} from "../types";

export interface SessionOutcomeModalProps {
  isOpen: boolean;
  onClose: () => void;
  session: {
    sessionId: string;
    classId?: string;
    startAt: string;
    endAt: string;
    studentName?: string;
  };
  plan?: PlanResponse | null;
  /** Cột mốc dự kiến của buổi (nếu gia sư đã gắn khi xếp lịch): chọn sẵn thay cho cột mốc đang học. */
  plannedMilestoneIds?: string[];
  /** Buổi đã được điểm danh "có mặt" từ trước (gia sư bấm "Để sau"): chỉ còn bổ sung phản hồi. */
  outcomeAlreadyRecorded?: boolean;
  onSuccess?: () => void;
}

interface TaughtState {
  level?: IndependenceLevel;
  correctCount: string;
  totalCount: string;
  evidenceNote: string;
  checkedCriterionIds: string[];
  markAchieved: boolean;
}

const activeMilestones = (plan?: PlanResponse | null): MilestoneResponse[] =>
  (plan?.milestones ?? []).filter((m) => !m.isArchived).sort((a, b) => a.sortOrder - b.sortOrder);

/**
 * Mặc định (1 chạm): cột mốc dự kiến của buổi nếu có; không thì cột mốc đang học — IN_PROGRESS đầu tiên,
 * nếu không có thì cột mốc chưa bắt đầu đầu tiên.
 */
const defaultMilestoneId = (milestones: MilestoneResponse[]): string | undefined =>
  (milestones.find((m) => m.status === "IN_PROGRESS") ?? milestones.find((m) => m.status === "NOT_STARTED"))
    ?.milestoneId;

const initialTaught = (m: MilestoneResponse): TaughtState => ({
  correctCount: "",
  totalCount: "",
  evidenceNote: "",
  checkedCriterionIds: (m.criteria ?? []).filter((c) => c.checked).map((c) => c.criterionId),
  markAchieved: false,
});

const toOptionalInt = (value: string): number | undefined => (value.trim() === "" ? undefined : Number(value));
const toOptionalText = (value: string): string | undefined => value.trim() || undefined;

const formatDateTime = (iso: string) => {
  try {
    return new Intl.DateTimeFormat("vi-VN", {
      weekday: "short",
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
};

export function SessionOutcomeModal({
  isOpen,
  onClose,
  session,
  plan,
  outcomeAlreadyRecorded = false,
  plannedMilestoneIds,
  onSuccess,
}: SessionOutcomeModalProps) {
  const milestones = useMemo(() => activeMilestones(plan), [plan]);
  const [outcome, setOutcome] = useState<"COMPLETED" | "ABSENT">("COMPLETED");
  const [noteContent, setNoteContent] = useState("");
  const [parentMessage, setParentMessage] = useState("");
  const [homework, setHomework] = useState<HomeworkStatus | null>(null);
  const [chips, setChips] = useState<AttitudeChip[]>([]);
  const [taught, setTaught] = useState<Record<string, TaughtState>>({});
  const [outcomeRecorded, setOutcomeRecorded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setOutcome("COMPLETED");
    setNoteContent("");
    setParentMessage("");
    setHomework(null);
    setChips([]);
    setOutcomeRecorded(outcomeAlreadyRecorded);
    setErrorMessage(null);
    const planned = milestones.filter(
      (m) => m.status !== "ACHIEVED" && (plannedMilestoneIds ?? []).includes(m.milestoneId)
    );
    const focus = milestones.find((m) => m.milestoneId === defaultMilestoneId(milestones));
    const preselected = planned.length > 0 ? planned : focus ? [focus] : [];
    setTaught(Object.fromEntries(preselected.map((m) => [m.milestoneId, initialTaught(m)])));
  }, [isOpen, session.sessionId, milestones, outcomeAlreadyRecorded, plannedMilestoneIds]);

  if (!isOpen) return null;

  const needsFeedback = outcome === "COMPLETED" && milestones.length > 0;

  const toggleTaught = (m: MilestoneResponse) =>
    setTaught((prev) => {
      if (prev[m.milestoneId]) {
        const rest = { ...prev };
        delete rest[m.milestoneId];
        return rest;
      }
      return { ...prev, [m.milestoneId]: initialTaught(m) };
    });

  const patchTaught = (milestoneId: string, patch: Partial<TaughtState>) =>
    setTaught((prev) => ({ ...prev, [milestoneId]: { ...prev[milestoneId], ...patch } }));

  const toggleCriterion = (m: MilestoneResponse, criterionId: string) => {
    const current = taught[m.milestoneId];
    const checked = current.checkedCriterionIds.includes(criterionId)
      ? current.checkedCriterionIds.filter((id) => id !== criterionId)
      : [...current.checkedCriterionIds, criterionId];
    const allChecked = checked.length === (m.criteria ?? []).length;
    patchTaught(m.milestoneId, {
      checkedCriterionIds: checked,
      markAchieved: allChecked && current.markAchieved,
      // Mức 4 chỉ hợp lệ khi tick đủ tiêu chí: bỏ tick thì bỏ chọn mức 4.
      level: !allChecked && current.level === 4 ? undefined : current.level,
    });
  };

  const toggleChip = (chip: AttitudeChip) =>
    setChips((prev) => (prev.includes(chip) ? prev.filter((c) => c !== chip) : [...prev, chip]));

  const buildFeedbackPayload = (): SubmitSessionFeedbackPayload | string => {
    const candidate = {
      homeworkStatus: homework ?? undefined,
      attitudeChips: chips,
      parentMessage: toOptionalText(parentMessage),
      milestones: milestones
        .filter((m) => taught[m.milestoneId])
        .map((m) => {
          const t = taught[m.milestoneId];
          return {
            milestoneId: m.milestoneId,
            level: t.level,
            correctCount: toOptionalInt(t.correctCount),
            totalCount: toOptionalInt(t.totalCount),
            evidenceNote: toOptionalText(t.evidenceNote),
            checkedCriterionIds: t.checkedCriterionIds,
            totalCriteria: (m.criteria ?? []).length,
            markAchieved: t.markAchieved,
          };
        }),
    };
    const parsed = sessionFeedbackSchema.safeParse(candidate);
    if (!parsed.success) {
      return parsed.error.issues[0]?.message ?? "Phản hồi buổi học chưa hợp lệ.";
    }
    return {
      homeworkStatus: parsed.data.homeworkStatus,
      attitudeChips: parsed.data.attitudeChips,
      parentMessage: parsed.data.parentMessage || undefined,
      milestones: parsed.data.milestones.map((m) => ({
        milestoneId: m.milestoneId,
        level: m.level,
        correctCount: m.correctCount,
        totalCount: m.totalCount,
        evidenceNote: m.evidenceNote || undefined,
        checkedCriterionIds: m.checkedCriterionIds,
        markAchieved: m.markAchieved,
      })),
    };
  };

  /** Ghi điểm danh, chưa phản hồi: hệ thống nhắc lúc +12h và +24h, sau 48h phản hồi được đánh dấu "bổ sung muộn". */
  const handleDefer = async () => {
    setErrorMessage(null);
    const outcomeValidation = recordSessionOutcomeSchema.safeParse({
      outcome,
      noteContent: noteContent.trim() || undefined,
    });
    if (!outcomeValidation.success) {
      setErrorMessage(outcomeValidation.error.issues[0]?.message ?? "Thông tin buổi học không hợp lệ.");
      return;
    }
    setLoading(true);
    try {
      const res = await progressApi.recordSessionOutcome(session.sessionId, {
        outcome,
        noteContent: noteContent.trim() || undefined,
      });
      if (!res.success) {
        setErrorMessage(res.message || "Không thể ghi nhận kết quả buổi học.");
        return;
      }
      onSuccess?.();
      onClose();
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Đã xảy ra lỗi khi lưu buổi học. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const outcomeValidation = recordSessionOutcomeSchema.safeParse({
      outcome,
      noteContent: noteContent.trim() || undefined,
    });
    if (!outcomeValidation.success) {
      setErrorMessage(outcomeValidation.error.issues[0]?.message ?? "Thông tin buổi học không hợp lệ.");
      return;
    }

    const feedbackPayload = needsFeedback ? buildFeedbackPayload() : null;
    if (typeof feedbackPayload === "string") {
      setErrorMessage(feedbackPayload);
      return;
    }

    setLoading(true);
    try {
      if (!outcomeRecorded) {
        const res = await progressApi.recordSessionOutcome(session.sessionId, {
          outcome,
          noteContent: noteContent.trim() || undefined,
        });
        if (!res.success) {
          setErrorMessage(res.message || "Không thể ghi nhận kết quả buổi học.");
          return;
        }
        setOutcomeRecorded(true);
      }

      if (feedbackPayload) {
        const res = await progressApi.submitSessionFeedback(session.sessionId, feedbackPayload);
        if (!res.success) {
          setErrorMessage(
            `Đã ghi nhận điểm danh nhưng chưa lưu được phản hồi: ${res.message || "lỗi không xác định"}. Hãy gửi lại phản hồi.`
          );
          return;
        }
      }

      onSuccess?.();
      onClose();
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Đã xảy ra lỗi khi lưu buổi học. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="outcome-modal-title"
      className="fixed inset-0 z-50 flex items-end sm:items-end sm:items-center justify-center sm:p-4 bg-night/60 backdrop-blur-xs"
    >
      <div className="relative w-full max-w-dialog-wide overflow-hidden rounded-t-3xl sm:rounded-3xl bg-surface shadow-2xl border border-line">
        <div className="flex items-center justify-between border-b border-line px-5 py-4 bg-paper/50">
          <div className="flex items-center gap-3">
            <div className="grid size-10 place-items-center rounded-2xl bg-success-soft text-success-strong">
              <FileText className="size-5" />
            </div>
            <div>
              <h2 id="outcome-modal-title" className="text-base font-medium text-ink">
                {outcomeAlreadyRecorded ? "Bổ sung phản hồi buổi học" : "Kết thúc buổi học"}
              </h2>
              <p className="text-xs text-muted mt-0.5 flex flex-wrap items-center gap-2">
                <Clock className="size-3 text-muted" />
                {formatDateTime(session.startAt)}
                {session.studentName && (
                  <>
                    <User className="size-3 text-muted" />
                    <span>{session.studentName}</span>
                  </>
                )}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-muted hover:bg-sunken hover:text-ink-2 cursor-pointer"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col max-h-[85vh]">
          <div className="p-5 space-y-5 overflow-y-auto">
            {errorMessage && (
              <div
                role="alert"
                className="flex items-start gap-3 rounded-2xl bg-danger-soft border border-danger-line p-4 text-xs text-danger-strong font-medium"
              >
                <AlertCircle className="size-4 text-danger-strong shrink-0 mt-0.5" />
                <p>{errorMessage}</p>
              </div>
            )}

            {/* 1. Điểm danh */}
            <fieldset className="space-y-2" disabled={outcomeRecorded}>
              <legend className="text-xs font-medium text-ink">
                Điểm danh <span className="text-danger-strong">*</span>
              </legend>
              <div className="grid grid-cols-2 gap-2">
                {(
                  [
                    { value: "COMPLETED", label: "Có mặt, đã học", Icon: CheckCircle2, tone: "emerald" },
                    { value: "ABSENT", label: "Vắng mặt", Icon: XCircle, tone: "rose" },
                  ] as const
                ).map(({ value, label, Icon, tone }) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={outcome === value}
                    onClick={() => setOutcome(value)}
                    className={`min-h-11 rounded-2xl border px-3 text-xs font-medium flex items-center justify-center gap-2 cursor-pointer disabled:cursor-not-allowed ${
                      outcome === value
                        ? tone === "emerald"
                          ? "border-success bg-success-soft text-success-strong"
                          : "border-danger bg-danger-soft text-danger-strong"
                        : "border-line bg-surface text-ink-2"
                    }`}
                  >
                    <Icon className="size-4" />
                    {label}
                  </button>
                ))}
              </div>
            </fieldset>

            {outcome === "COMPLETED" && milestones.length === 0 && (
              <p className="rounded-xl bg-action-soft border border-action-line p-3 text-xs text-action-strong">
                Lớp chưa có lộ trình nên chỉ ghi điểm danh. Hãy lập lộ trình để chấm mức tự lập theo cột mốc.
              </p>
            )}

            {needsFeedback && (
              <>
                {/* 2. Cột mốc được dạy + mức tự lập */}
                <section className="space-y-2" aria-labelledby="taught-heading">
                  <h3 id="taught-heading" className="text-xs font-medium text-ink">
                    Cột mốc dạy hôm nay & mức tự lập <span className="text-danger-strong">*</span>
                  </h3>
                  <div className="flex flex-wrap gap-1.5">
                    {milestones.map((m) => (
                      <button
                        key={m.milestoneId}
                        type="button"
                        aria-pressed={Boolean(taught[m.milestoneId])}
                        onClick={() => toggleTaught(m)}
                        className={`min-h-9 rounded-full border px-3 text-xs font-medium cursor-pointer ${
                          taught[m.milestoneId]
                            ? "border-action bg-action-soft text-action-strong"
                            : "border-line bg-surface text-ink-2"
                        }`}
                      >
                        {m.title}
                      </button>
                    ))}
                  </div>

                  {milestones
                    .filter((m) => taught[m.milestoneId])
                    .map((m) => {
                      const t = taught[m.milestoneId];
                      const criteria = m.criteria ?? [];
                      const allChecked = t.checkedCriterionIds.length === criteria.length;
                      const alreadyAchieved = m.status === "ACHIEVED";
                      return (
                        <div
                          key={m.milestoneId}
                          className="rounded-2xl border border-line p-3 space-y-3"
                          data-testid={`taught-milestone-${m.milestoneId}`}
                        >
                          <p className="text-xs font-medium text-ink">{m.title}</p>
                          <div
                            role="radiogroup"
                            aria-label={`Mức tự lập — ${m.title}`}
                            className="grid grid-cols-2 sm:grid-cols-4 gap-1.5"
                          >
                            {INDEPENDENCE_LEVELS.map((lvl) => (
                              <button
                                key={lvl.value}
                                type="button"
                                role="radio"
                                aria-checked={t.level === lvl.value}
                                disabled={lvl.value === 4 && !allChecked}
                                title={
                                  lvl.value === 4 && !allChecked ? "Cần tick đủ tiêu chí đạt để chấm mức 4" : lvl.label
                                }
                                onClick={() => patchTaught(m.milestoneId, { level: lvl.value })}
                                className={`min-h-11 rounded-xl border px-2 text-xs font-medium leading-tight cursor-pointer disabled:cursor-not-allowed disabled:opacity-40 ${
                                  t.level === lvl.value
                                    ? "border-action bg-action text-on-action"
                                    : "border-line bg-paper text-ink-2"
                                }`}
                              >
                                {lvl.value}. {lvl.short}
                              </button>
                            ))}
                          </div>

                          {criteria.length > 0 && (
                            <div className="space-y-1">
                              <p className="text-xs font-medium text-ink-2">Tiêu chí đạt</p>
                              {criteria.map((c) => (
                                <label key={c.criterionId} className="flex items-start gap-2 text-xs text-ink">
                                  <input
                                    type="checkbox"
                                    className="mt-0.5 size-4"
                                    checked={t.checkedCriterionIds.includes(c.criterionId)}
                                    disabled={alreadyAchieved}
                                    onChange={() => toggleCriterion(m, c.criterionId)}
                                  />
                                  {c.content}
                                </label>
                              ))}
                            </div>
                          )}

                          <div className="flex flex-wrap items-center gap-2 text-xs text-ink-2">
                            <span>Đúng</span>
                            <input
                              type="number"
                              min={0}
                              inputMode="numeric"
                              aria-label={`Số bài đúng — ${m.title}`}
                              value={t.correctCount}
                              onChange={(e) => patchTaught(m.milestoneId, { correctCount: e.target.value })}
                              className="w-14 rounded-lg border border-line px-2 py-1"
                            />
                            <span>/</span>
                            <input
                              type="number"
                              min={1}
                              max={100}
                              inputMode="numeric"
                              aria-label={`Tổng số bài — ${m.title}`}
                              value={t.totalCount}
                              onChange={(e) => patchTaught(m.milestoneId, { totalCount: e.target.value })}
                              className="w-14 rounded-lg border border-line px-2 py-1"
                            />
                            <span className="text-muted">bài (tùy chọn)</span>
                          </div>
                          <input
                            type="text"
                            aria-label={`Bằng chứng — ${m.title}`}
                            value={t.evidenceNote}
                            maxLength={EVIDENCE_NOTE_MAX_LENGTH}
                            onChange={(e) => patchTaught(m.milestoneId, { evidenceNote: e.target.value })}
                            placeholder="Bằng chứng (bắt buộc nếu tăng từ 2 mức trở lên và không nhập đúng x/y)"
                            className="w-full rounded-lg border border-line px-2 py-1.5 text-xs placeholder:text-muted"
                          />

                          {!alreadyAchieved && (
                            <label
                              className={`flex items-center gap-2 text-xs font-medium ${
                                allChecked ? "text-success-strong" : "text-muted"
                              }`}
                            >
                              <input
                                type="checkbox"
                                className="size-4"
                                checked={t.markAchieved}
                                disabled={!allChecked}
                                onChange={(e) => patchTaught(m.milestoneId, { markAchieved: e.target.checked })}
                              />
                              Chốt đạt cột mốc này
                              {!allChecked && <span className="font-normal">(cần tick đủ tiêu chí)</span>}
                            </label>
                          )}
                        </div>
                      );
                    })}
                </section>

                {/* 3. Việc về nhà */}
                <fieldset className="space-y-2">
                  <legend className="text-xs font-medium text-ink">
                    Việc về nhà <span className="text-danger-strong">*</span>
                  </legend>
                  <div className="flex flex-wrap gap-1.5">
                    {HOMEWORK_OPTIONS.map((opt) => (
                      <button
                        key={opt.value}
                        type="button"
                        aria-pressed={homework === opt.value}
                        onClick={() => setHomework(opt.value)}
                        className={`min-h-9 rounded-full border px-3 text-xs font-medium cursor-pointer ${
                          homework === opt.value
                            ? "border-success bg-success-soft text-success-strong"
                            : "border-line bg-surface text-ink-2"
                        }`}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </fieldset>

                {/* 4. Thái độ (tùy chọn) */}
                <fieldset className="space-y-2">
                  <legend className="text-xs font-medium text-ink">
                    Thái độ, nỗ lực <span className="font-normal normal-case text-muted">(tùy chọn)</span>
                  </legend>
                  <div className="flex flex-wrap gap-1.5">
                    {ATTITUDE_CHIPS.map((chip) => (
                      <button
                        key={chip.value}
                        type="button"
                        aria-pressed={chips.includes(chip.value)}
                        onClick={() => toggleChip(chip.value)}
                        className={`min-h-9 rounded-full border px-3 text-xs font-medium cursor-pointer ${
                          chips.includes(chip.value)
                            ? "border-brand bg-brand-soft text-brand-strong"
                            : "border-line bg-surface text-ink-2"
                        }`}
                      >
                        {chip.label}
                      </button>
                    ))}
                  </div>
                </fieldset>
              </>
            )}

            {/* 5. Nhận xét ngắn */}
            <div className="space-y-1.5">
              <label
                htmlFor="session-note-content"
                className="block text-xs font-medium text-ink"
              >
                Một câu nhận xét <span className="font-normal normal-case text-muted">(tùy chọn)</span>
              </label>
              <input
                id="session-note-content"
                type="text"
                value={noteContent}
                disabled={outcomeRecorded}
                onChange={(e) => setNoteContent(e.target.value)}
                maxLength={FEEDBACK_COMMENT_MAX_LENGTH}
                placeholder="Ví dụ: Con giải hệ phương trình nhanh hơn, cần cẩn thận dấu khi chuyển vế."
                className="w-full rounded-xl border border-line bg-paper/50 px-3 py-2.5 text-xs text-ink placeholder:text-muted focus:bg-surface focus:border-success focus:outline-hidden"
              />
              <p className="text-right text-xs text-muted">
                {noteContent.length}/{FEEDBACK_COMMENT_MAX_LENGTH}
              </p>
            </div>

            {/* 6. Lời nhắn riêng cho phụ huynh */}
            {needsFeedback && (
              <div className="space-y-1.5">
                <label
                  htmlFor="session-parent-message"
                  className="block text-xs font-medium text-ink"
                >
                  Lời nhắn riêng cho phụ huynh{" "}
                  <span className="font-normal normal-case text-muted">(tùy chọn, học sinh không thấy)</span>
                </label>
                <textarea
                  id="session-parent-message"
                  rows={2}
                  value={parentMessage}
                  onChange={(e) => setParentMessage(e.target.value)}
                  maxLength={PARENT_MESSAGE_MAX_LENGTH}
                  placeholder="Ví dụ: Gia đình nhắc con ngủ sớm hơn trước hôm có buổi học."
                  className="w-full rounded-xl border border-line bg-paper/50 px-3 py-2.5 text-xs text-ink placeholder:text-muted focus:bg-surface focus:border-success focus:outline-hidden"
                />
              </div>
            )}
          </div>

          <div className="flex items-center justify-end gap-2 border-t border-line px-5 py-4 bg-paper/50">
            <button
              type="button"
              onClick={onClose}
              className="min-h-10 px-4 rounded-xl border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer"
            >
              Đóng
            </button>
            {needsFeedback && !outcomeRecorded && (
              <button
                type="button"
                onClick={handleDefer}
                disabled={loading}
                title="Chỉ ghi điểm danh. Hệ thống nhắc lại sau 12 và 24 giờ; sau 48 giờ phản hồi được đánh dấu bổ sung muộn."
                className="min-h-10 px-4 rounded-xl border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer disabled:opacity-50"
              >
                Để sau
              </button>
            )}
            <button
              type="submit"
              disabled={loading}
              className="min-h-10 px-5 rounded-xl bg-success hover:bg-success-deep text-white text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
            >
              {loading ? <Loader2 className="size-3.5 animate-spin" /> : <CheckCircle2 className="size-3.5" />}
              {outcomeRecorded ? (outcomeAlreadyRecorded ? "Lưu phản hồi" : "Gửi lại phản hồi") : "Lưu buổi học"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
