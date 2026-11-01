"use client";

import React, { useEffect, useMemo, useState } from "react";
import { connectionService } from "@/services/connections";
import { profileService, type LearningGoalData, type LearningGoalType } from "@/services/profile";
import { formatMatchingSlots } from "@/shared/lib/dateTime";
import { AlertCircle, Bell, BookOpen, Clock, Sparkles } from "lucide-react";

export interface TutorSubjectOption {
  subjectId: number;
  subjectName: string;
  educationLevelId: number;
  educationLevelName: string;
}

export interface ConnectionTutorInfo {
  tutorId: string;
  displayName: string;
  subjects?: TutorSubjectOption[];
  pricePerSession?: number;
  availabilitySlots?: Array<{ dayOfWeek: number; startTime: string; endTime: string }>;
  teachingMode?: "ONLINE" | "OFFLINE" | "BOTH";
  area?: string | null;
}

export interface ConnectionRequestDialogProps {
  tutorId: string;
  tutor?: ConnectionTutorInfo | null;
  goal?: LearningGoalData;
  goals?: LearningGoalData[];
  userRole?: "STUDENT" | "PARENT" | "TUTOR" | "ADMIN";
  selectedChildName?: string;
  onSelectGoal?: (goal: LearningGoalData) => void;
  onGoalCreated?: (newGoal: LearningGoalData) => void;
  onClose: () => void;
  onSent: () => void;
}

function getGoalTypeLabel(type?: string): string {
  switch (type) {
    case "CONSOLIDATE":
      return "Củng cố kiến thức";
    case "IMPROVE_GRADES":
      return "Cải thiện điểm số";
    case "ADVANCED_STUDY":
      return "Bồi dưỡng nâng cao";
    case "EXAM_PREPARATION":
      return "Ôn thi chuyển cấp";
    case "OTHER":
      return "Mục tiêu khác";
    default:
      return type || "";
  }
}

function getModeLabel(mode?: string): string {
  switch (mode) {
    case "ONLINE":
      return "Online";
    case "OFFLINE":
      return "Offline";
    case "BOTH":
      return "Online & Offline";
    default:
      return mode || "";
  }
}

function formatGoalOptionLabel(g: LearningGoalData): string {
  const typeText = getGoalTypeLabel(g.goalType);
  const modeText = getModeLabel(g.mode);
  const slotsText = formatMatchingSlots(g.availabilitySlots);
  const budgetText = g.budgetMax ? `${Math.round(g.budgetMax / 1000)}k/h` : "";
  const extraParts = [typeText, modeText, slotsText, budgetText].filter(Boolean);
  const extra = extraParts.length > 0 ? ` — ${extraParts.join(" · ")}` : "";
  return `${g.subjectName} · ${g.educationLevelName}${extra}`;
}

export function ConnectionRequestDialog({
  tutorId,
  tutor,
  goal: initialGoal,
  goals = [],
  userRole = "STUDENT",
  selectedChildName,
  onSelectGoal,
  onGoalCreated,
  onClose,
  onSent,
}: ConnectionRequestDialogProps) {
  const tutorSubjects = useMemo(() => tutor?.subjects || [], [tutor]);

  // Determine initial subject
  const initialSubject = useMemo(() => {
    if (tutorSubjects.length === 0) return null;
    if (initialGoal) {
      const exactMatch = tutorSubjects.find(
        (s) =>
          s.subjectId === initialGoal.subjectId &&
          (!initialGoal.educationLevelId || s.educationLevelId === initialGoal.educationLevelId)
      );
      if (exactMatch) return exactMatch;
      const subjectMatch = tutorSubjects.find((s) => s.subjectId === initialGoal.subjectId);
      if (subjectMatch) return subjectMatch;
    }
    return tutorSubjects[0];
  }, [tutorSubjects, initialGoal]);

  const [selectedSubject, setSelectedSubject] = useState<TutorSubjectOption | null>(initialSubject);

  // Update selected subject when initialSubject changes
  useEffect(() => {
    if (initialSubject) {
      setSelectedSubject((prev) => {
        if (!prev) return initialSubject;
        const exists = tutorSubjects.some(
          (s) => s.subjectId === prev.subjectId && s.educationLevelId === prev.educationLevelId
        );
        return exists ? prev : initialSubject;
      });
    }
  }, [initialSubject, tutorSubjects]);

  // Matching goals for selected subject
  const matchingGoals = useMemo(() => {
    const allGoals = goals.length > 0 ? goals : initialGoal ? [initialGoal] : [];
    if (!selectedSubject) return allGoals;
    return allGoals.filter(
      (g) =>
        g.subjectId === selectedSubject.subjectId &&
        (!selectedSubject.educationLevelId || g.educationLevelId === selectedSubject.educationLevelId)
    );
  }, [goals, initialGoal, selectedSubject]);

  const [selectedGoal, setSelectedGoal] = useState<LearningGoalData | null>(() => {
    if (initialGoal && matchingGoals.some((g) => g.goalId === initialGoal.goalId)) {
      return initialGoal;
    }
    return matchingGoals[0] || initialGoal || (goals[0] ?? null);
  });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reminderSent, setReminderSent] = useState(false);

  // Sync selected goal when matchingGoals change
  useEffect(() => {
    if (matchingGoals.length > 0) {
      setSelectedGoal((prev) => {
        if (prev && matchingGoals.some((g) => g.goalId === prev.goalId)) {
          return prev;
        }
        if (initialGoal && matchingGoals.some((g) => g.goalId === initialGoal.goalId)) {
          return initialGoal;
        }
        return matchingGoals[0];
      });
    } else {
      setSelectedGoal(null);
    }
  }, [matchingGoals, initialGoal]);

  // Quick goal form states (for STUDENT creating a goal inline)
  const [quickGoalType, setQuickGoalType] = useState<LearningGoalType>("IMPROVE_GRADES");
  const [quickMode, setQuickMode] = useState<"ONLINE" | "OFFLINE" | "BOTH">(
    tutor?.teachingMode === "OFFLINE" ? "OFFLINE" : "ONLINE"
  );
  const [quickArea, setQuickArea] = useState<string>(tutor?.area || "");
  const [quickBudgetMax, setQuickBudgetMax] = useState<number>(tutor?.pricePerSession || 300000);

  const defaultSlots = useMemo(() => {
    if (tutor?.availabilitySlots && tutor.availabilitySlots.length > 0) {
      return tutor.availabilitySlots.slice(0, 2);
    }
    return [
      { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
    ];
  }, [tutor]);

  const [quickSlots, setQuickSlots] = useState<Array<{ dayOfWeek: number; startTime: string; endTime: string }>>(
    defaultSlots
  );

  useEffect(() => {
    setQuickSlots(defaultSlots);
  }, [defaultSlots]);

  // List of other subjects student has goals for (for PARENT helper)
  const subjectsWithGoals = useMemo(() => {
    const map = new Map<number, { subjectId: number; subjectName: string; count: number }>();
    for (const g of goals) {
      const existing = map.get(g.subjectId);
      if (existing) {
        existing.count++;
      } else {
        map.set(g.subjectId, { subjectId: g.subjectId, subjectName: g.subjectName, count: 1 });
      }
    }
    return Array.from(map.values());
  }, [goals]);

  // Send request with an existing selected goal
  const sendExistingGoal = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy || !selectedGoal) return;
    setBusy(true);
    setError(null);
    try {
      await connectionService.send({
        goalId: selectedGoal.goalId,
        tutorId,
        message: message.trim() || undefined,
        desiredSlots: selectedGoal.availabilitySlots.map((slot) => formatMatchingSlots([slot])),
      });
      onSent();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể gửi yêu cầu.");
    } finally {
      setBusy(false);
    }
  };

  // Create new goal inline and send request
  const handleCreateAndSend = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy || !selectedSubject) return;
    setBusy(true);
    setError(null);
    try {
      // Set deadline 90 days in future
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 90);
      const deadline = futureDate.toISOString().split("T")[0];

      const createdGoal = await profileService.createLearningGoal({
        subjectId: selectedSubject.subjectId,
        currentLevel: 1,
        desiredLevel: 3,
        goalType: quickGoalType,
        deadline,
        mode: quickMode,
        area: quickMode === "ONLINE" ? undefined : quickArea.trim() || undefined,
        budgetMax: quickBudgetMax,
        availabilitySlots: quickSlots,
      });

      onGoalCreated?.(createdGoal);
      setSelectedGoal(createdGoal);

      await connectionService.send({
        goalId: createdGoal.goalId,
        tutorId,
        message: message.trim() || undefined,
        desiredSlots: createdGoal.availabilitySlots.map((slot) => formatMatchingSlots([slot])),
      });

      onSent();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể tạo mục tiêu và gửi yêu cầu.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="connection-title"
        className="w-full max-w-lg space-y-4 rounded-3xl bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 p-6 shadow-2xl max-h-[92vh] overflow-y-auto"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-stone-100 dark:border-stone-800 pb-3">
          <div>
            <h3 id="connection-title" className="text-base font-bold text-neutral-900 dark:text-neutral-100">
              Gửi yêu cầu kết nối gia sư
            </h3>
            {tutor && (
              <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-0.5">
                Gia sư: <span className="font-semibold text-neutral-800 dark:text-neutral-200">{tutor.displayName}</span>
                {tutor.pricePerSession ? ` · ${tutor.pricePerSession.toLocaleString("vi-VN")} đ/buổi` : ""}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng hộp thoại"
            className="size-7 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-600 dark:hover:text-neutral-200 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer text-sm"
          >
            ✕
          </button>
        </div>

        {/* 1. Môn gia sư nhận giảng dạy (Tutor-first subject selector) */}
        {tutorSubjects.length > 0 && (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="block text-xs font-bold text-neutral-800 dark:text-neutral-200">
                Môn học gia sư giảng dạy ({tutorSubjects.length})
              </label>
              <span className="text-[11px] text-neutral-400">Chọn môn bạn cần kèm cặp</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {tutorSubjects.map((s) => {
                const isSelected =
                  selectedSubject?.subjectId === s.subjectId &&
                  (!s.educationLevelId || selectedSubject?.educationLevelId === s.educationLevelId);
                const hasMatchingGoal = goals.some((g) => g.subjectId === s.subjectId);
                return (
                  <button
                    key={`${s.subjectId}-${s.educationLevelId}`}
                    type="button"
                    onClick={() => {
                      setSelectedSubject(s);
                      setReminderSent(false);
                    }}
                    className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all cursor-pointer flex items-center gap-1.5 ${
                      isSelected
                        ? "bg-blue-600 text-white border-blue-600 shadow-2xs font-bold"
                        : "bg-stone-50 dark:bg-stone-800/90 text-neutral-700 dark:text-neutral-300 border-stone-200 dark:border-stone-700 hover:border-blue-400"
                    }`}
                  >
                    <span>{s.subjectName} · {s.educationLevelName}</span>
                    {hasMatchingGoal && (
                      <span
                        className={`text-[10px] px-1.5 py-0.2 rounded-full ${
                          isSelected
                            ? "bg-white/25 text-white"
                            : "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 font-normal"
                        }`}
                      >
                        Đã có mục tiêu
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* 2. CASE A: Đã có mục tiêu học tập khớp môn này */}
        {matchingGoals.length > 0 && selectedGoal && (
          <form onSubmit={sendExistingGoal} className="space-y-4">
            {matchingGoals.length > 1 && (
              <div className="space-y-1">
                <label htmlFor="goal-picker" className="block text-xs font-semibold text-neutral-700 dark:text-neutral-300">
                  Chọn mục tiêu học tập ghép lớp
                </label>
                <select
                  id="goal-picker"
                  value={selectedGoal.goalId}
                  onChange={(e) => {
                    const found = matchingGoals.find((g) => g.goalId === e.target.value);
                    if (found) {
                      setSelectedGoal(found);
                      onSelectGoal?.(found);
                    }
                  }}
                  className="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 p-2 text-xs font-medium text-neutral-900 dark:text-neutral-100 focus:border-blue-600 focus:outline-hidden"
                >
                  {matchingGoals.map((g) => (
                    <option key={g.goalId} value={g.goalId}>
                      {formatGoalOptionLabel(g)}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {/* Chi tiết mục tiêu đã chọn */}
            <div className="rounded-2xl border border-stone-100 dark:border-stone-800 bg-stone-50/80 dark:bg-stone-800/50 p-3.5 space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <p className="font-semibold text-neutral-900 dark:text-neutral-100 text-sm">
                  {selectedGoal.subjectName} · {selectedGoal.educationLevelName}
                  {getGoalTypeLabel(selectedGoal.goalType) && ` — ${getGoalTypeLabel(selectedGoal.goalType)}`}
                  {getModeLabel(selectedGoal.mode) && ` (${getModeLabel(selectedGoal.mode)})`}
                </p>
                {selectedGoal.budgetMax ? (
                  <span className="shrink-0 text-xs font-bold text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/60 px-2 py-0.5 rounded-md border border-amber-200/60 dark:border-amber-800/60">
                    Tối đa {selectedGoal.budgetMax.toLocaleString("vi-VN")} đ/buổi
                  </span>
                ) : null}
              </div>
              <p className="text-xs text-neutral-500 dark:text-neutral-400">
                Khung giờ đề xuất: {formatMatchingSlots(selectedGoal.availabilitySlots) || "Chưa có khung giờ"}
              </p>
              {selectedGoal.deadline && (
                <p className="text-[11px] text-neutral-400 dark:text-neutral-500">
                  Thời hạn mục tiêu: {selectedGoal.deadline}
                </p>
              )}
            </div>

            {/* Lời nhắn */}
            <label className="block text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Lời nhắn gửi gia sư (không bắt buộc)
              <textarea
                className="mt-1 w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2.5 text-xs text-neutral-900 dark:text-neutral-100 focus:border-blue-600 focus:outline-hidden"
                maxLength={2000}
                rows={3}
                placeholder="Ví dụ: Mong muốn học thử buổi đầu tiên, cải thiện kiến thức hình học..."
                value={message}
                onChange={(event) => setMessage(event.target.value)}
              />
            </label>

            {error && (
              <p role="alert" className="text-xs text-rose-700 dark:text-rose-400 font-medium">
                {error}
              </p>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                disabled={busy}
                onClick={onClose}
                className="rounded-full border border-stone-200 dark:border-stone-700 px-4 py-2 text-xs font-semibold text-neutral-700 dark:text-neutral-300 hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer"
              >
                Đóng
              </button>
              <button
                disabled={busy || !selectedGoal.availabilitySlots.length}
                className="rounded-full bg-blue-700 px-5 py-2 text-xs font-bold text-white hover:bg-blue-800 disabled:opacity-50 cursor-pointer"
              >
                {busy ? "Đang gửi…" : "Xác nhận gửi yêu cầu"}
              </button>
            </div>
          </form>
        )}

        {/* 3. CASE B: Chưa có mục tiêu - Vai trò PHỤ HUYNH */}
        {matchingGoals.length === 0 && userRole === "PARENT" && selectedSubject && (
          <div className="space-y-4">
            <div className="rounded-2xl border border-amber-200 bg-amber-50/70 dark:bg-amber-950/30 dark:border-amber-800 p-4 space-y-3">
              <div className="flex items-start gap-2.5">
                <AlertCircle className="size-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
                <div className="space-y-1">
                  <h4 className="text-xs font-bold text-amber-900 dark:text-amber-200">
                    Chưa có mục tiêu học tập môn {selectedSubject?.subjectName}
                  </h4>
                  <p className="text-xs text-amber-800 dark:text-amber-300 leading-relaxed">
                    Học sinh <span className="font-semibold">{selectedChildName || "con"}</span> hiện chưa thiết lập mục tiêu học tập môn{" "}
                    <strong>
                      {selectedSubject?.subjectName} · {selectedSubject?.educationLevelName}
                    </strong>
                    . Gia sư cần mục tiêu học tập để chuẩn bị kế hoạch giảng dạy phù hợp.
                  </p>
                </div>
              </div>

              {subjectsWithGoals.length > 0 && (
                <div className="pt-2 border-t border-amber-200/60 dark:border-amber-800/60 space-y-1.5">
                  <p className="text-[11px] font-semibold text-amber-900 dark:text-amber-200">
                    Hoặc chọn môn con đã có sẵn mục tiêu để kết nối ngay:
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {subjectsWithGoals.map((subj) => {
                      const foundInTutor = tutorSubjects.find((s) => s.subjectId === subj.subjectId);
                      if (!foundInTutor) return null;
                      return (
                        <button
                          key={subj.subjectId}
                          type="button"
                          onClick={() => setSelectedSubject(foundInTutor)}
                          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-white dark:bg-stone-800 text-xs font-semibold text-amber-900 dark:text-amber-100 border border-amber-300 dark:border-amber-700 shadow-2xs hover:bg-amber-100 dark:hover:bg-amber-900/50 transition-colors cursor-pointer"
                        >
                          <span>{subj.subjectName} ({subj.count} mục tiêu)</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              <div className="flex items-center justify-between pt-1">
                <button
                  type="button"
                  onClick={() => setReminderSent(true)}
                  disabled={reminderSent}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-amber-900 dark:text-amber-100 bg-amber-200/80 hover:bg-amber-200 dark:bg-amber-900/80 transition-colors cursor-pointer disabled:opacity-60"
                >
                  <Bell className="size-3.5" />
                  <span>{reminderSent ? "Đã gửi nhắc nhở cho con" : "Nhắc con tạo mục tiêu môn này"}</span>
                </button>
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="rounded-full border border-stone-200 dark:border-stone-700 px-5 py-2 text-xs font-semibold text-neutral-700 dark:text-neutral-300 hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer"
              >
                Đóng
              </button>
            </div>
          </div>
        )}

        {/* 4. CASE C: Chưa có mục tiêu - Vai trò HỌC SINH (Khởi tạo nhanh Inline Goal) */}
        {matchingGoals.length === 0 && userRole !== "PARENT" && selectedSubject && (
          <form onSubmit={handleCreateAndSend} className="space-y-4">
            <div className="rounded-2xl border border-blue-200 dark:border-blue-900 bg-blue-50/60 dark:bg-blue-950/40 p-4 space-y-3">
              <div className="flex items-start gap-2.5">
                <Sparkles className="size-5 text-blue-600 dark:text-blue-400 shrink-0 mt-0.5" />
                <div>
                  <h4 className="text-xs font-bold text-blue-950 dark:text-blue-100">
                    Khởi tạo nhanh mục tiêu môn {selectedSubject.subjectName} · {selectedSubject.educationLevelName}
                  </h4>
                  <p className="text-xs text-blue-800 dark:text-blue-300 mt-0.5">
                    Bạn chưa có mục tiêu môn này. Điền nhanh các thông tin dưới đây để gửi yêu cầu kết nối ngay mà không cần rời trang:
                  </p>
                </div>
              </div>

              {/* Form fields */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="block text-[11px] font-bold text-neutral-700 dark:text-neutral-300 mb-1">
                    Loại mục tiêu
                  </label>
                  <select
                    value={quickGoalType}
                    onChange={(e) => setQuickGoalType(e.target.value as LearningGoalType)}
                    className="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2 text-xs text-neutral-900 dark:text-neutral-100 focus:outline-hidden"
                  >
                    <option value="IMPROVE_GRADES">Cải thiện điểm số</option>
                    <option value="CONSOLIDATE">Củng cố kiến thức</option>
                    <option value="ADVANCED_STUDY">Bồi dưỡng nâng cao</option>
                    <option value="EXAM_PREPARATION">Ôn thi chuyển cấp</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] font-bold text-neutral-700 dark:text-neutral-300 mb-1">
                    Hình thức học
                  </label>
                  <select
                    value={quickMode}
                    onChange={(e) => setQuickMode(e.target.value as "ONLINE" | "OFFLINE" | "BOTH")}
                    className="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2 text-xs text-neutral-900 dark:text-neutral-100 focus:outline-hidden"
                  >
                    <option value="ONLINE">Học Online</option>
                    <option value="OFFLINE">Học Offline tại nhà</option>
                    <option value="BOTH">Linh hoạt Online & Offline</option>
                  </select>
                </div>
              </div>

              {quickMode !== "ONLINE" && (
                <div>
                  <label className="block text-[11px] font-bold text-neutral-700 dark:text-neutral-300 mb-1">
                    Khu vực học kèm
                  </label>
                  <input
                    type="text"
                    value={quickArea}
                    onChange={(e) => setQuickArea(e.target.value)}
                    placeholder="Ví dụ: Quận Cầu Giấy, Hà Nội"
                    className="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2 text-xs text-neutral-900 dark:text-neutral-100 focus:outline-hidden"
                  />
                </div>
              )}

              {/* Slots selector */}
              <div>
                <label className="block text-[11px] font-bold text-neutral-700 dark:text-neutral-300 mb-1">
                  Khung giờ đề xuất học ({quickSlots.length} buổi/tuần)
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {[
                    { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00", label: "Tối Thứ 3 (19:00 - 21:00)" },
                    { dayOfWeek: 4, startTime: "19:00:00", endTime: "21:00:00", label: "Tối Thứ 5 (19:00 - 21:00)" },
                    { dayOfWeek: 6, startTime: "19:00:00", endTime: "21:00:00", label: "Tối Thứ 7 (19:00 - 21:00)" },
                    { dayOfWeek: 7, startTime: "09:00:00", endTime: "11:00:00", label: "Sáng CN (09:00 - 11:00)" },
                  ].map((preset, idx) => {
                    const isSelected = quickSlots.some(
                      (s) => s.dayOfWeek === preset.dayOfWeek && s.startTime === preset.startTime
                    );
                    return (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => {
                          if (isSelected) {
                            if (quickSlots.length > 1) {
                              setQuickSlots(
                                quickSlots.filter(
                                  (s) => !(s.dayOfWeek === preset.dayOfWeek && s.startTime === preset.startTime)
                                )
                              );
                            }
                          } else {
                            setQuickSlots([
                              ...quickSlots,
                              { dayOfWeek: preset.dayOfWeek, startTime: preset.startTime, endTime: preset.endTime },
                            ]);
                          }
                        }}
                        className={`px-2.5 py-1 rounded-lg text-xs font-medium border transition-colors cursor-pointer ${
                          isSelected
                            ? "bg-blue-600 border-blue-600 text-white font-bold"
                            : "bg-white dark:bg-stone-800 border-stone-200 dark:border-stone-700 text-neutral-700 dark:text-neutral-300 hover:bg-stone-50"
                        }`}
                      >
                        {preset.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-neutral-700 dark:text-neutral-300 mb-1">
                  Ngân sách tối đa (đ/buổi)
                </label>
                <input
                  type="number"
                  step={10000}
                  min={50000}
                  max={2000000}
                  value={quickBudgetMax}
                  onChange={(e) => setQuickBudgetMax(Number(e.target.value))}
                  className="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2 text-xs text-neutral-900 dark:text-neutral-100 focus:outline-hidden"
                />
              </div>
            </div>

            {/* Lời nhắn */}
            <label className="block text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Lời nhắn gửi gia sư (không bắt buộc)
              <textarea
                className="mt-1 w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-2.5 text-xs text-neutral-900 dark:text-neutral-100 focus:border-blue-600 focus:outline-hidden"
                maxLength={2000}
                rows={2}
                placeholder="Ví dụ: Em muốn học thử buổi đầu tiên..."
                value={message}
                onChange={(event) => setMessage(event.target.value)}
              />
            </label>

            {error && (
              <p role="alert" className="text-xs text-rose-700 dark:text-rose-400 font-medium">
                {error}
              </p>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                disabled={busy}
                onClick={onClose}
                className="rounded-full border border-stone-200 dark:border-stone-700 px-4 py-2 text-xs font-semibold text-neutral-700 dark:text-neutral-300 hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer"
              >
                Đóng
              </button>
              <button
                disabled={busy || quickSlots.length === 0}
                className="rounded-full bg-blue-700 px-5 py-2 text-xs font-bold text-white hover:bg-blue-800 disabled:opacity-50 cursor-pointer"
              >
                {busy ? "Đang xử lý…" : "Khởi tạo mục tiêu & Gửi yêu cầu"}
              </button>
            </div>
          </form>
        )}

        {/* 5. CASE D: Gia sư chưa có môn học và chưa có mục tiêu khớp */}
        {!selectedSubject && matchingGoals.length === 0 && (
          <div className="space-y-4 py-4 text-center">
            <div className="p-4 rounded-2xl bg-stone-50 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 space-y-2">
              <AlertCircle className="size-8 text-amber-500 mx-auto" />
              <h4 className="text-xs font-bold text-neutral-800 dark:text-neutral-200">
                Chưa có thông tin môn học giảng dạy
              </h4>
              <p className="text-xs text-neutral-500 dark:text-neutral-400 max-w-sm mx-auto">
                Gia sư này hiện chưa cập nhật danh sách môn học nhận dạy, hoặc bạn chưa có mục tiêu học tập đang hoạt động.
              </p>
            </div>
            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={onClose}
                className="rounded-full border border-stone-200 dark:border-stone-700 px-5 py-2 text-xs font-semibold text-neutral-700 dark:text-neutral-300 hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer"
              >
                Đóng
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
