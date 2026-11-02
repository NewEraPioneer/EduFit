"use client";

import React, { useState, useId } from "react";
import {
  Calendar,
  Clock,
  Plus,
  Trash2,
  Video,
  MapPin,
  AlertCircle,
  X,
  CheckCircle2,
  CalendarDays,
  AlertTriangle,
} from "lucide-react";
import { schedulingApi, isConflictError, getConflictMessage } from "../api/schedulingApi";
import type { SessionMode } from "../types";

const DAYS_OF_WEEK = [
  { value: "MONDAY", label: "Thứ Hai" },
  { value: "TUESDAY", label: "Thứ Ba" },
  { value: "WEDNESDAY", label: "Thứ Tư" },
  { value: "THURSDAY", label: "Thứ Năm" },
  { value: "FRIDAY", label: "Thứ Sáu" },
  { value: "SATURDAY", label: "Thứ Bảy" },
  { value: "SUNDAY", label: "Chủ Nhật" },
] as const;

type DayOfWeek = (typeof DAYS_OF_WEEK)[number]["value"];

interface SlotItem {
  id: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}

export interface ProposeRecurringScheduleModalProps {
  isOpen: boolean;
  onClose: () => void;
  classId: string;
  className?: string;
  tutorName?: string;
  studentName?: string;
  isReschedule?: boolean;
  onSuccess?: () => void;
}

function formatDateToIso(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr + "T00:00:00");
  d.setDate(d.getDate() + days);
  return formatDateToIso(d);
}

function daysBetween(startStr: string, endStr: string): number {
  const start = new Date(startStr + "T00:00:00").getTime();
  const end = new Date(endStr + "T00:00:00").getTime();
  return Math.round((end - start) / (1000 * 60 * 60 * 24));
}

export function ProposeRecurringScheduleModal({
  isOpen,
  onClose,
  classId,
  className,
  tutorName,
  studentName,
  isReschedule = false,
  onSuccess,
}: ProposeRecurringScheduleModalProps) {
  const titleId = useId();

  // Mặc định ngày bắt đầu từ ngày mai
  const tomorrowStr = React.useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return formatDateToIso(d);
  }, []);

  const [effectiveFrom, setEffectiveFrom] = useState<string>(tomorrowStr);
  const [horizonWeeks, setHorizonWeeks] = useState<number>(4);
  const [effectiveTo, setEffectiveTo] = useState<string>(() => addDays(tomorrowStr, 28));
  const [slots, setSlots] = useState<SlotItem[]>([
    { id: "1", dayOfWeek: "MONDAY", startTime: "18:00", endTime: "20:00" },
  ]);
  const [mode, setMode] = useState<SessionMode>("ONLINE");
  const [placeOrLink, setPlaceOrLink] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Cập nhật effectiveTo khi effectiveFrom hoặc horizonWeeks thay đổi
  const handleWeeksChange = (weeks: number) => {
    setHorizonWeeks(weeks);
    setEffectiveTo(addDays(effectiveFrom, weeks * 7));
  };

  const handleFromChange = (newFrom: string) => {
    setEffectiveFrom(newFrom);
    setEffectiveTo(addDays(newFrom, horizonWeeks * 7));
  };

  const addSlot = () => {
    const newId = String(Date.now());
    setSlots((prev) => [
      ...prev,
      { id: newId, dayOfWeek: "WEDNESDAY", startTime: "18:00", endTime: "20:00" },
    ]);
  };

  const removeSlot = (id: string) => {
    setSlots((prev) => prev.filter((s) => s.id !== id));
  };

  const updateSlot = (id: string, updates: Partial<SlotItem>) => {
    setSlots((prev) => prev.map((s) => (s.id === id ? { ...s, ...updates } : s)));
  };

  if (!isOpen) return null;

  const durationDays = daysBetween(effectiveFrom, effectiveTo);
  const isHorizonValid = durationDays >= 28 && durationDays <= 56;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // 1. Kiểm tra bounded horizon
    if (!isHorizonValid) {
      setError(
        `Khoảng thời gian áp dụng phải từ 4 đến 8 tuần (28 đến 56 ngày). Hiện tại: ${durationDays} ngày.`
      );
      return;
    }

    // 2. Kiểm tra slots
    if (slots.length === 0) {
      setError("Vui lòng thêm ít nhất một khung giờ học trong tuần.");
      return;
    }

    // Kiểm tra từng slot
    for (const slot of slots) {
      if (!slot.startTime || !slot.endTime) {
        setError("Vui lòng nhập đầy đủ giờ bắt đầu và kết thúc cho các khung giờ.");
        return;
      }
      if (slot.startTime >= slot.endTime) {
        setError(
          `Khung giờ ${slot.dayOfWeek}: Giờ bắt đầu (${slot.startTime}) phải trước giờ kết thúc (${slot.endTime}).`
        );
        return;
      }
    }

    // Kiểm tra trùng slot nội bộ
    for (let i = 0; i < slots.length; i++) {
      for (let j = i + 1; j < slots.length; j++) {
        const a = slots[i];
        const b = slots[j];
        if (a.dayOfWeek === b.dayOfWeek) {
          if (a.startTime < b.endTime && b.startTime < a.endTime) {
            setError(
              `Hai khung giờ trong ngày ${a.dayOfWeek} bị chồng lấn thời gian (${a.startTime}-${a.endTime} và ${b.startTime}-${b.endTime}).`
            );
            return;
          }
        }
      }
    }

    const newWeeklySlots = slots.map(
      (s) => `${s.dayOfWeek} ${s.startTime}-${s.endTime}`
    );

    try {
      setSubmitting(true);
      await schedulingApi.proposeRecurringSchedule({
        classId,
        effectiveFrom,
        effectiveTo,
        newWeeklySlots,
        mode,
        placeOrLink: placeOrLink.trim() || undefined,
        message: message.trim() || undefined,
      });

      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("edufit:schedule-updated"));
        try {
          localStorage.setItem("edufit:schedule-updated", String(Date.now()));
        } catch {
          // ignore
        }
      }
      onSuccess?.();
      onClose();
    } catch (err: unknown) {
      if (isConflictError(err)) {
        setError(getConflictMessage(err, "Khung giờ học định kỳ bị trùng lặp với lịch hiện có. Vui lòng chọn khung giờ khác."));
      } else {
        setError(
          err instanceof Error
            ? err.message
            : "Đã có lỗi xảy ra khi đề xuất lịch học định kỳ. Vui lòng thử lại."
        );
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-200"
    >
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto rounded-3xl bg-white p-6 shadow-2xl dark:bg-zinc-900 dark:border dark:border-zinc-800">
        {/* Header */}
        <div className="flex items-start justify-between pb-4 border-b border-zinc-100 dark:border-zinc-800">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300 mb-1.5">
              <CalendarDays className="h-3.5 w-3.5" />
              {isReschedule ? "Đề xuất lại lịch định kỳ (UC4.8)" : "Lập lịch định kỳ (UC4.8)"}
            </div>
            <h2 id={titleId} className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
              {isReschedule ? "Đề xuất lại lịch học định kỳ" : "Thống nhất lịch học định kỳ"}
            </h2>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
              {className ? `Lớp ${className}` : "Lớp học"}{" "}
              {tutorName && `• Gia sư: ${tutorName}`} {studentName && `• Học sinh: ${studentName}`}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-300 cursor-pointer"
            aria-label="Đóng"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Cảnh báo nổi bật khi đề xuất lại lịch định kỳ */}
        {isReschedule && (
          <div
            role="alert"
            className="mt-4 rounded-2xl border border-amber-300 bg-amber-50 p-4 text-xs text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300 flex items-start gap-3"
          >
            <AlertTriangle className="size-5 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
            <div>
              <p className="font-bold text-sm">Lưu ý quan trọng khi đề xuất lại lịch:</p>
              <p className="mt-1 leading-relaxed">
                Khi đề xuất mới được đối tác chấp thuận, <strong>toàn bộ lịch học cũ trong tương lai của lớp sẽ tự động bị hủy</strong> và được thay thế bằng loạt buổi học mới theo khung giờ thỏa thuận này.
              </p>
            </div>
          </div>
        )}

        {/* Error Alert */}
        {error && (
          <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700 dark:border-rose-900/40 dark:bg-rose-950/30 dark:text-rose-300 flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <div className="flex-1 font-medium">{error}</div>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {/* 1. Bounded Horizon (Chu kỳ áp dụng) */}
          <div className="rounded-2xl border border-zinc-200/80 bg-zinc-50/50 p-4 dark:border-zinc-800 dark:bg-zinc-800/30 space-y-3">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 flex items-center gap-1.5">
                <Calendar className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                Chu kỳ áp dụng (Bounded Horizon: 4 - 8 tuần)
              </label>
              <span
                className={`text-[11px] font-medium px-2 py-0.5 rounded-full ${
                  isHorizonValid
                    ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
                    : "bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300"
                }`}
              >
                {durationDays} ngày ({Math.round((durationDays / 7) * 10) / 10} tuần)
              </span>
            </div>

            {/* Horizon weeks selector quick buttons */}
            <div className="flex items-center gap-1.5">
              <span className="text-xs text-zinc-500 dark:text-zinc-400">Chọn nhanh:</span>
              {[4, 5, 6, 7, 8].map((w) => (
                <button
                  key={w}
                  type="button"
                  onClick={() => handleWeeksChange(w)}
                  className={`rounded-lg px-2.5 py-1 text-xs font-medium transition-all ${
                    horizonWeeks === w
                      ? "bg-blue-600 text-white shadow-xs"
                      : "border border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
                  }`}
                >
                  {w} tuần
                </button>
              ))}
            </div>

            {/* Date inputs */}
            <div className="grid grid-cols-2 gap-3 pt-1">
              <div>
                <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400 block mb-1">
                  Từ ngày
                </label>
                <input
                  type="date"
                  value={effectiveFrom}
                  onChange={(e) => handleFromChange(e.target.value)}
                  className="w-full rounded-xl border border-zinc-300 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:border-blue-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  required
                />
              </div>
              <div>
                <label className="text-[11px] font-medium text-zinc-600 dark:text-zinc-400 block mb-1">
                  Đến ngày
                </label>
                <input
                  type="date"
                  value={effectiveTo}
                  onChange={(e) => setEffectiveTo(e.target.value)}
                  className="w-full rounded-xl border border-zinc-300 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:border-blue-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  required
                />
              </div>
            </div>
          </div>

          {/* 2. Khung giờ học định kỳ trong tuần */}
          <div className="rounded-2xl border border-zinc-200/80 bg-zinc-50/50 p-4 dark:border-zinc-800 dark:bg-zinc-800/30 space-y-3">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 flex items-center gap-1.5">
                <Clock className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                Khung giờ học định kỳ trong tuần ({slots.length} buổi/tuần)
              </label>
              <button
                type="button"
                onClick={addSlot}
                className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 dark:text-blue-400"
              >
                <Plus className="h-3.5 w-3.5" />
                Thêm khung giờ
              </button>
            </div>

            <div className="space-y-2">
              {slots.map((slot, index) => (
                <div
                  key={slot.id}
                  className="flex items-center gap-2 rounded-xl border border-zinc-200 bg-white p-2.5 dark:border-zinc-700 dark:bg-zinc-800"
                >
                  <span className="text-xs font-bold text-zinc-400 w-5 text-center">
                    #{index + 1}
                  </span>

                  {/* Day of Week */}
                  <select
                    value={slot.dayOfWeek}
                    onChange={(e) =>
                      updateSlot(slot.id, { dayOfWeek: e.target.value as DayOfWeek })
                    }
                    className="flex-1 rounded-lg border border-zinc-300 bg-zinc-50 px-2 py-1 text-xs font-medium text-zinc-800 focus:border-blue-500 focus:outline-none dark:border-zinc-600 dark:bg-zinc-700 dark:text-zinc-200"
                  >
                    {DAYS_OF_WEEK.map((d) => (
                      <option key={d.value} value={d.value}>
                        {d.label}
                      </option>
                    ))}
                  </select>

                  {/* Start Time */}
                  <input
                    type="time"
                    value={slot.startTime}
                    onChange={(e) => updateSlot(slot.id, { startTime: e.target.value })}
                    className="w-24 rounded-lg border border-zinc-300 bg-zinc-50 px-2 py-1 text-xs text-zinc-800 focus:border-blue-500 focus:outline-none dark:border-zinc-600 dark:bg-zinc-700 dark:text-zinc-200"
                  />

                  <span className="text-zinc-400 text-xs">→</span>

                  {/* End Time */}
                  <input
                    type="time"
                    value={slot.endTime}
                    onChange={(e) => updateSlot(slot.id, { endTime: e.target.value })}
                    className="w-24 rounded-lg border border-zinc-300 bg-zinc-50 px-2 py-1 text-xs text-zinc-800 focus:border-blue-500 focus:outline-none dark:border-zinc-600 dark:bg-zinc-700 dark:text-zinc-200"
                  />

                  {/* Delete button */}
                  {slots.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeSlot(slot.id)}
                      className="p-1 rounded-md text-zinc-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30"
                      title="Xóa khung giờ"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* 3. Hình thức và địa điểm */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 block mb-1">
                Hình thức học
              </label>
              <div className="flex rounded-xl border border-zinc-300 p-0.5 bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-800">
                <button
                  type="button"
                  onClick={() => setMode("ONLINE")}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 text-xs font-medium rounded-lg transition-all ${
                    mode === "ONLINE"
                      ? "bg-white text-blue-600 shadow-xs dark:bg-zinc-700 dark:text-blue-400"
                      : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
                  }`}
                >
                  <Video className="h-3.5 w-3.5" />
                  Trực tuyến (Online)
                </button>
                <button
                  type="button"
                  onClick={() => setMode("OFFLINE")}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 text-xs font-medium rounded-lg transition-all ${
                    mode === "OFFLINE"
                      ? "bg-white text-blue-600 shadow-xs dark:bg-zinc-700 dark:text-blue-400"
                      : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
                  }`}
                >
                  <MapPin className="h-3.5 w-3.5" />
                  Trực tiếp (Offline)
                </button>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 block mb-1">
                {mode === "ONLINE" ? "Link phòng học trực tuyến" : "Địa điểm học"}
              </label>
              <input
                type="text"
                value={placeOrLink}
                onChange={(e) => setPlaceOrLink(e.target.value)}
                placeholder={
                  mode === "ONLINE"
                    ? "https://meet.google.com/..."
                    : "Số nhà, đường, quận/huyện..."
                }
                className="w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-xs text-zinc-900 focus:border-blue-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
              />
            </div>
          </div>

          {/* 4. Lời nhắn / Ghi chú */}
          <div>
            <label className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 block mb-1">
              Ghi chú / Lời nhắn cho bên kia (tùy chọn)
            </label>
            <textarea
              rows={2}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="VD: Chúng ta sẽ bắt đầu học lịch định kỳ 2 buổi/tuần từ tuần tới nhé..."
              className="w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-xs text-zinc-900 focus:border-blue-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 resize-none"
            />
          </div>

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-2 pt-3 border-t border-zinc-100 dark:border-zinc-800">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-blue-700 active:scale-95 disabled:opacity-50 transition-all cursor-pointer"
            >
              {submitting ? (
                <>
                  <span className="h-3.5 w-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Đang gửi đề xuất...
                </>
              ) : (
                <>
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  {isReschedule ? "Gửi đề xuất lại lịch định kỳ" : "Gửi đề xuất lịch định kỳ"}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
