"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { AlertCircle, Loader2, Plus } from "lucide-react";
import { connectionService } from "@/services/connections";
import { learningNeedService, type LearningNeed, type WeeklySlot } from "@/services/learningNeeds";
import type { TutorOffering } from "@/services/profile";
import { formatMatchingSlots, validateAvailabilitySlots } from "@/shared/lib/dateTime";
import { AvailabilitySlotPicker } from "@/features/profile/components/AvailabilitySlotPicker";
import { describeMissing } from "@/features/learning-needs/catalogRules";
import { LearningNeedModal } from "@/features/learning-needs/components/LearningNeedModal";
import { LearningNeedSummary, needTitle } from "@/features/learning-needs/components/LearningNeedSummary";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";
import { FitChecks } from "@/features/discovery/components/FitChecks";
import { formatVnd, offeringName } from "@/features/discovery/utils/tutorDisplay";
import type { TutorFit } from "@/features/discovery/types";

export interface ConnectionTutorInfo {
  tutorId: string;
  displayName: string;
  /** Gói dạy của gia sư; khi có, chỉ nhu cầu thuộc môn gia sư dạy mới được chọn. */
  offerings?: TutorOffering[];
}

export interface ConnectionRequestDialogProps {
  tutorId: string;
  tutor?: ConnectionTutorInfo | null;
  /** Phụ huynh truyền studentId của con đã liên kết; học sinh để trống. */
  studentId?: string;
  /** Nhu cầu đang chọn ở màn trước (ví dụ AI Match), được chọn sẵn nếu hợp lệ. */
  initialNeedId?: string;
  onClose: () => void;
  onSent: () => void;
  /** Báo cho màn gọi khi nhu cầu được tạo hoặc bổ sung trong hộp thoại. */
  onNeedSaved?: (need: LearningNeed) => void;
}

const MESSAGE_MAX = 500;

/**
 * Gửi yêu cầu kết nối gắn với một nhu cầu học. Độ khớp chỉ là cảnh báo mềm (⚠), không chặn gửi;
 * backend chỉ yêu cầu nhu cầu đủ thông tin và gia sư có gói dạy môn đó.
 */
export function ConnectionRequestDialog({
  tutorId,
  tutor,
  studentId,
  initialNeedId,
  onClose,
  onSent,
  onNeedSaved,
}: ConnectionRequestDialogProps) {
  const [needs, setNeeds] = useState<LearningNeed[]>([]);
  const [loading, setLoading] = useState(true);
  const [needId, setNeedId] = useState(initialNeedId ?? "");
  const [fit, setFit] = useState<TutorFit | null>(null);
  const [fitLoading, setFitLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [proposedSlots, setProposedSlots] = useState<WeeklySlot[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needDialog, setNeedDialog] = useState<{ need: LearningNeed | null; subjectId?: number } | null>(null);

  const offerings = tutor?.offerings;
  const taughtSubjects = useMemo(
    () => (offerings ? new Set(offerings.map((offering) => offering.subjectId)) : null),
    [offerings]
  );
  const eligible = useMemo(
    () => needs.filter((need) => !taughtSubjects || taughtSubjects.has(need.subjectId)),
    [needs, taughtSubjects]
  );
  const selected = eligible.find((need) => need.needId === needId) ?? null;
  const slotValidation = validateAvailabilitySlots(proposedSlots);

  useEffect(() => {
    setProposedSlots(selected?.availabilitySlots.map((slot) => ({ ...slot })) ?? []);
  }, [selected]);

  const loadNeeds = useCallback(async (selectNeedId?: string) => {
    setLoading(true);
    try {
      const list = (await learningNeedService.list(studentId)).filter((need) => need.status !== "ARCHIVED");
      setNeeds(list);
      const usable = list.filter((need) => !taughtSubjects || taughtSubjects.has(need.subjectId));
      setNeedId((current) => {
        const preferred = selectNeedId ?? current;
        if (usable.some((need) => need.needId === preferred)) return preferred;
        return (usable.find((need) => need.status === "ACTIVE") ?? usable[0])?.needId ?? "";
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không tải được nhu cầu học.");
    } finally {
      setLoading(false);
    }
  }, [studentId, taughtSubjects]);

  useEffect(() => {
    void loadNeeds();
  }, [loadNeeds]);

  useEffect(() => {
    setFit(null);
    if (!selected || selected.status !== "ACTIVE") return;
    let cancelled = false;
    setFitLoading(true);
    discoveryApi.getFit(selected.needId, tutorId)
      .then((response) => { if (!cancelled) setFit(response.data?.fit ?? null); })
      .catch(() => { if (!cancelled) setFit(null); })
      .finally(() => { if (!cancelled) setFitLoading(false); });
    return () => { cancelled = true; };
  }, [selected, tutorId]);

  const send = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy || !selected || selected.status !== "ACTIVE" || !slotValidation.valid) return;
    setBusy(true);
    setError(null);
    try {
      await connectionService.send({
        needId: selected.needId,
        tutorId,
        message: message.trim() || undefined,
        desiredSlots: proposedSlots.map((slot) => formatMatchingSlots([slot])),
      });
      onSent();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể gửi yêu cầu.");
    } finally {
      setBusy(false);
    }
  };

  const subjectsTaught = offerings
    ? Array.from(new Map(offerings.map((offering) => [offering.subjectId, offering])).values())
    : [];

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs overflow-y-auto">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="connection-title"
        className="w-full max-w-lg space-y-4 rounded-3xl bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 p-5 sm:p-6 shadow-2xl my-auto"
      >
        <div className="flex items-start justify-between gap-3 border-b border-stone-100 dark:border-stone-800 pb-3">
          <div className="min-w-0">
            <h3 id="connection-title" className="text-base font-bold text-neutral-900 dark:text-neutral-100">
              Gửi yêu cầu kết nối gia sư
            </h3>
            {tutor && (
              <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-0.5 break-words">
                Gia sư: <span className="font-semibold text-neutral-800 dark:text-neutral-200">{tutor.displayName}</span>
              </p>
            )}
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng hộp thoại"
            className="size-7 shrink-0 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-600 hover:bg-stone-100 dark:hover:bg-stone-800 text-sm">
            ✕
          </button>
        </div>

        {loading ? (
          <p className="text-xs text-neutral-500 inline-flex items-center gap-2"><Loader2 className="size-4 animate-spin" />Đang tải nhu cầu học…</p>
        ) : eligible.length === 0 ? (
          <div className="space-y-4">
            <div className="rounded-2xl border border-amber-200 bg-amber-50/70 dark:bg-amber-950/30 dark:border-amber-800 p-4 flex items-start gap-2.5">
              <AlertCircle className="size-5 text-amber-600 shrink-0 mt-0.5" />
              <div className="space-y-1 text-xs text-amber-900 dark:text-amber-200">
                <p className="font-bold">Chưa có nhu cầu học cho môn gia sư này dạy</p>
                <p>Gia sư cần biết lớp, môn, mục tiêu và lịch học để chuẩn bị. Tạo nhu cầu cho một môn bên dưới.</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              {(subjectsTaught.length > 0 ? subjectsTaught : [null]).map((offering) => (
                <button key={offering?.subjectId ?? "any"} type="button"
                  onClick={() => setNeedDialog({ need: null, subjectId: offering?.subjectId })}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-stone-200 text-xs font-semibold text-neutral-800 hover:bg-stone-50">
                  <Plus className="size-3.5" />
                  {offering ? `Tạo nhu cầu môn ${offeringName({ subjectName: offering.subjectName, trackName: null })}` : "Tạo nhu cầu học"}
                </button>
              ))}
            </div>
            <div className="flex justify-end">
              <button type="button" onClick={onClose}
                className="rounded-full border border-stone-200 dark:border-stone-700 px-5 py-2 text-xs font-semibold text-neutral-700 dark:text-neutral-300 hover:bg-stone-50">
                Đóng
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={send} className="space-y-4">
            <label className="block text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Nhu cầu học gửi kèm yêu cầu
              <select value={needId} onChange={(event) => setNeedId(event.target.value)}
                className="mt-1 w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 p-2 text-xs font-medium text-neutral-900 dark:text-neutral-100">
                {eligible.map((need) => (
                  <option key={need.needId} value={need.needId}>
                    {needTitle(need)}{need.status === "INCOMPLETE" ? " (cần bổ sung)" : ""}
                  </option>
                ))}
              </select>
            </label>

            {selected && (
              <div className="rounded-2xl border border-stone-100 dark:border-stone-800 bg-stone-50/80 dark:bg-stone-800/50 p-3.5 space-y-2">
                <LearningNeedSummary need={selected} />
                {selected.status === "INCOMPLETE" && (
                  <button type="button" onClick={() => setNeedDialog({ need: selected })}
                    className="px-3 py-1.5 rounded-xl bg-[#006DCC] text-white text-xs font-bold">
                    Bổ sung: {describeMissing(selected.missingFields)}
                  </button>
                )}
                {fitLoading && <p className="text-[11px] text-neutral-500">Đang so khớp với gia sư…</p>}
                {fit && (
                  <div className="space-y-1.5 pt-1 border-t border-stone-200/70">
                    <p className="text-xs text-neutral-700 dark:text-neutral-300 break-words">
                      Gói dạy: <strong>{fit.offeringName}</strong> · {formatVnd(fit.pricePerSession)}/buổi · {Math.round(fit.matchScore)}% phù hợp
                    </p>
                    <FitChecks checks={fit.checks} />
                    {fit.checks.some((check) => !check.matched) && (
                      <p className="text-[11px] text-amber-700">Vẫn có thể gửi; gia sư sẽ thấy các điểm chưa khớp để cân nhắc.</p>
                    )}
                  </div>
                )}
              </div>
            )}

            {selected?.status === "ACTIVE" && (
              <details className="rounded-2xl border border-stone-200 bg-white p-3.5">
                <summary className="cursor-pointer text-xs font-semibold text-sky-700">Đổi lịch đề xuất cho gia sư này</summary>
                <p className="mt-2 text-xs text-neutral-600">Lịch ban đầu lấy từ nhu cầu học. Thay đổi ở đây chỉ áp dụng cho yêu cầu gửi đến gia sư này.</p>
                <div className="mt-3 max-h-64 overflow-y-auto">
                  <AvailabilitySlotPicker slots={proposedSlots} onChange={setProposedSlots} title="Lịch muốn đề xuất" />
                </div>
              </details>
            )}

            <label className="block text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Lời nhắn gửi gia sư (không bắt buộc)
              <textarea
                className="mt-1 w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2.5 text-xs text-neutral-900 dark:text-neutral-100"
                maxLength={MESSAGE_MAX}
                rows={3}
                placeholder="Ví dụ: Mong muốn học thử buổi đầu tiên, cần củng cố phần hình học..."
                value={message}
                onChange={(event) => setMessage(event.target.value)}
              />
              <span className="block text-right text-[11px] font-normal text-neutral-400">{message.length}/{MESSAGE_MAX}</span>
            </label>

            {error && <p role="alert" className="text-xs text-rose-700 dark:text-rose-400 font-medium">{error}</p>}

            <div className="flex flex-wrap justify-between gap-3 pt-1">
              <button type="button" onClick={() => setNeedDialog({ need: null, subjectId: selected?.subjectId })}
                className="inline-flex items-center gap-1 text-xs font-semibold text-[#006DCC]">
                <Plus className="size-3.5" />Nhu cầu khác
              </button>
              <div className="flex gap-3">
                <button type="button" disabled={busy} onClick={onClose}
                  className="rounded-full border border-stone-200 dark:border-stone-700 px-4 py-2 text-xs font-semibold text-neutral-700 dark:text-neutral-300 hover:bg-stone-50">
                  Đóng
                </button>
                <button disabled={busy || !selected || selected.status !== "ACTIVE" || !slotValidation.valid}
                  className="rounded-full bg-blue-700 px-5 py-2 text-xs font-bold text-white hover:bg-blue-800 disabled:opacity-50">
                  {busy ? "Đang gửi…" : "Xác nhận gửi yêu cầu"}
                </button>
              </div>
            </div>
          </form>
        )}
      </div>

      {needDialog && (
        <LearningNeedModal
          isOpen
          need={needDialog.need}
          studentId={studentId}
          defaults={needDialog.need ? undefined : latestDefaults(needs, needDialog.subjectId)}
          title={needDialog.need ? "Bổ sung thông tin nhu cầu học" : undefined}
          onClose={() => setNeedDialog(null)}
          onSaved={async (saved) => {
            setNeedDialog(null);
            onNeedSaved?.(saved);
            await loadNeeds(saved.needId);
          }}
        />
      )}
    </div>
  );
}

/** Điền sẵn điều kiện học từ nhu cầu gần nhất, kèm môn được chọn. */
function latestDefaults(needs: LearningNeed[], subjectId?: number) {
  const latest = needs.at(-1);
  return {
    ...(latest && {
      educationLevelId: latest.educationLevelId ?? undefined,
      mode: latest.mode,
      provinceId: latest.provinceId,
      districtId: latest.districtId,
      budgetMaxPerSession: latest.budgetMaxPerSession,
      availabilitySlots: latest.availabilitySlots,
    }),
    ...(subjectId !== undefined && { subjectId }),
  };
}
