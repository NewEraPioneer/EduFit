"use client";

import React, { useState, useEffect } from "react";
import { X, CalendarPlus, AlertTriangle, Video, MapPin, Loader2 } from "lucide-react";
import { ApiError } from "@/lib/api";
import { schedulingApi, isConflictError, getConflictMessage } from "../api/schedulingApi";
import { proposeSessionSchema } from "../schemas/schedulingSchemas";
import {
  type SessionDetail,
  type ConnectedClass,
  type SessionMode,
  type ProposeSessionPayload,
  formatConnectedClassName,
} from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";
import { formatLocalDateToIso } from "@/shared/utils/dateTime";

export interface ProposeSessionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (session: SessionDetail) => void;
  role: UserRole;
  currentUserId?: string;
  initialDate?: Date;
  initialHour?: number;
  connectedClasses?: ConnectedClass[];
}

export function ProposeSessionModal({
  isOpen,
  onClose,
  onSuccess,
  role,
  currentUserId,
  initialDate,
  initialHour = 8,
  connectedClasses = [],
}: ProposeSessionModalProps) {
  // Pre-fill date string (YYYY-MM-DD)
  const defaultDateStr = formatLocalDateToIso(initialDate || new Date());

  const defaultStartTimeStr = `${String(initialHour).padStart(2, "0")}:00`;
  const defaultEndTimeStr = `${String(Math.min(initialHour + 1, 23)).padStart(2, "0")}:00`;

  const [selectedClassId, setSelectedClassId] = useState<string>("");
  const [dateStr, setDateStr] = useState<string>(defaultDateStr);
  const [startTimeStr, setStartTimeStr] = useState<string>(defaultStartTimeStr);
  const [endTimeStr, setEndTimeStr] = useState<string>(defaultEndTimeStr);
  const [mode, setMode] = useState<SessionMode>("ONLINE");
  const [placeOrLink, setPlaceOrLink] = useState<string>("");
  const [message, setMessage] = useState<string>("");

  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);

  const availableClasses = React.useMemo(() => {
    return connectedClasses.filter((c) => !c.status || c.status === "ACTIVE");
  }, [connectedClasses]);

  // Sync initial date/hour when modal opens
  useEffect(() => {
    if (isOpen) {
      if (initialDate) {
        setDateStr(formatLocalDateToIso(initialDate));
      }
      setStartTimeStr(`${String(initialHour).padStart(2, "0")}:00`);
      setEndTimeStr(`${String(Math.min(initialHour + 1, 23)).padStart(2, "0")}:00`);
      setErrorMessage(null);
      setIsConflict(false);

      if (availableClasses.length > 0) {
        if (!selectedClassId || !availableClasses.some((c) => c.classId === selectedClassId)) {
          setSelectedClassId(availableClasses[0].classId);
        }
      } else {
        setSelectedClassId("");
      }
    }
  }, [isOpen, initialDate, initialHour, availableClasses, selectedClassId]);

  if (!isOpen) return null;

  const currentClass = availableClasses.find((c) => c.classId === selectedClassId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setIsConflict(false);

    if (!currentClass) {
      setErrorMessage("Vui lòng chọn lớp học đang hoạt động để đề xuất buổi học.");
      return;
    }

    const classId = currentClass.classId;
    const tutorId = currentClass.tutorId;
    const studentId = currentClass.studentId;

    if (!classId) {
      setErrorMessage("Vui lòng chọn lớp học đang hoạt động để đề xuất buổi học.");
      return;
    }

    // Construct ISO datetimes in local timezone
    const startDateTime = new Date(`${dateStr}T${startTimeStr}:00`);
    const endDateTime = new Date(`${dateStr}T${endTimeStr}:00`);

    if (Number.isNaN(startDateTime.getTime()) || Number.isNaN(endDateTime.getTime())) {
      setErrorMessage("Thời gian học không hợp lệ.");
      return;
    }

    const payload: ProposeSessionPayload = {
      classId,
      tutorId: tutorId || undefined,
      studentId: studentId || undefined,
      startAt: startDateTime.toISOString(),
      endAt: endDateTime.toISOString(),
      mode,
      placeOrLink: placeOrLink.trim() || undefined,
      message: message.trim() || undefined,
    };

    // Validate with Zod schema
    const validation = proposeSessionSchema.safeParse(payload);
    if (!validation.success) {
      const issue = validation.error.issues[0];
      setErrorMessage(issue?.message || "Dữ liệu đề xuất lịch học không hợp lệ.");
      return;
    }

    try {
      setLoading(true);
      const res = await schedulingApi.proposeSession(payload);
      if (res.success && res.data) {
        onSuccess(res.data);
        onClose();
      } else {
        setErrorMessage(res.message || "Không thể đề xuất lịch học.");
      }
    } catch (err: unknown) {
      if (isConflictError(err)) {
        setIsConflict(true);
        setErrorMessage(
          getConflictMessage(
            err,
            "Khung giờ này đã bị trùng với lịch khác! Vui lòng chọn một khung giờ khác."
          )
        );
      } else if (err instanceof ApiError && err.details && err.details.length > 0) {
        const detailMsg = err.details.map((d) => d.message).filter(Boolean).join(", ");
        setErrorMessage(detailMsg || err.message);
      } else {
        setErrorMessage(
          err instanceof Error
            ? err.message
            : "Đã xảy ra lỗi khi tạo đề xuất lịch học. Vui lòng thử lại."
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="propose-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs overflow-y-auto"
    >
      <div className="relative w-full max-w-lg overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 animate-in fade-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-stone-100 px-6 py-4">
          <div className="flex items-center gap-2.5">
            <div className="grid size-9 place-items-center rounded-xl bg-sky-100 text-sky-800">
              <CalendarPlus className="size-5" />
            </div>
            <div>
              <h2 id="propose-modal-title" className="text-base font-bold text-neutral-900">
                Đề xuất buổi học mới
              </h2>
              <p className="text-xs text-neutral-500">
                {role === "TUTOR" ? "Gia sư đề xuất lịch dạy" : "Học sinh/Phụ huynh đề xuất lịch học"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 hover:text-neutral-700 transition-colors"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 max-h-[80vh] overflow-y-auto">
          {/* Cảnh báo lỗi / Trùng lịch 409 */}
          {errorMessage && (
            <div
              role="alert"
              className={`flex items-start gap-3 rounded-2xl p-4 text-xs font-medium leading-relaxed ${
                isConflict
                  ? "bg-rose-50 text-rose-800 border border-rose-200"
                  : "bg-amber-50 text-amber-800 border border-amber-200"
              }`}
            >
              <AlertTriangle className={`size-4 shrink-0 mt-0.5 ${isConflict ? "text-rose-600" : "text-amber-600"}`} />
              <div>
                <p className="font-bold">{isConflict ? "Khung giờ bị trùng lịch!" : "Không thể tạo lịch"}</p>
                <p className="mt-0.5">{errorMessage}</p>
              </div>
            </div>
          )}

          {/* Chọn lớp học */}
          {availableClasses.length > 0 ? (
            <div>
              <label htmlFor="select-class" className="block text-xs font-bold text-neutral-700 mb-1">
                Lớp học <span className="text-rose-500">*</span>
              </label>
              <select
                id="select-class"
                value={selectedClassId}
                onChange={(e) => setSelectedClassId(e.target.value)}
                className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-xs font-semibold text-neutral-800 focus:border-sky-500 focus:outline-hidden"
              >
                {availableClasses.map((c) => (
                  <option key={c.classId} value={c.classId}>
                    {formatConnectedClassName(c, role, c.classId)}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div
              role="status"
              className="flex items-start gap-3 rounded-2xl bg-amber-50 p-4 border border-amber-200 text-amber-800 text-xs font-medium"
            >
              <AlertTriangle className="size-4 shrink-0 mt-0.5 text-amber-600" />
              <div>
                <p className="font-bold">Chưa có lớp học hoạt động</p>
                <p className="mt-0.5 leading-relaxed">
                  Bạn chưa có lớp học nào đang hoạt động để lên lịch. Vui lòng kiểm tra lại kết nối gia sư.
                </p>
              </div>
            </div>
          )}

          {/* Ngày học & Khung giờ */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div>
              <label htmlFor="session-date" className="block text-xs font-bold text-neutral-700 mb-1">
                Ngày học <span className="text-rose-500">*</span>
              </label>
              <input
                id="session-date"
                type="date"
                min={formatLocalDateToIso(new Date())}
                value={dateStr}
                onChange={(e) => setDateStr(e.target.value)}
                className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-xs font-semibold text-neutral-800 focus:border-sky-500 focus:outline-hidden"
                required
              />
            </div>

            <div>
              <label htmlFor="session-start-time" className="block text-xs font-bold text-neutral-700 mb-1">
                Giờ bắt đầu <span className="text-rose-500">*</span>
              </label>
              <input
                id="session-start-time"
                type="time"
                value={startTimeStr}
                onChange={(e) => setStartTimeStr(e.target.value)}
                className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-xs font-semibold text-neutral-800 focus:border-sky-500 focus:outline-hidden"
                required
              />
            </div>

            <div>
              <label htmlFor="session-end-time" className="block text-xs font-bold text-neutral-700 mb-1">
                Giờ kết thúc <span className="text-rose-500">*</span>
              </label>
              <input
                id="session-end-time"
                type="time"
                value={endTimeStr}
                onChange={(e) => setEndTimeStr(e.target.value)}
                className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-xs font-semibold text-neutral-800 focus:border-sky-500 focus:outline-hidden"
                required
              />
            </div>
          </div>

          {/* Hình thức học */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Hình thức học <span className="text-rose-500">*</span>
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setMode("ONLINE")}
                className={`flex items-center justify-center gap-2 rounded-xl border py-2.5 px-3 text-xs font-bold transition-colors cursor-pointer ${
                  mode === "ONLINE"
                    ? "border-sky-500 bg-sky-50 text-sky-800"
                    : "border-stone-200 bg-white text-neutral-600 hover:bg-stone-50"
                }`}
              >
                <Video className="size-4" />
                Trực tuyến (Online)
              </button>
              <button
                type="button"
                onClick={() => setMode("OFFLINE")}
                className={`flex items-center justify-center gap-2 rounded-xl border py-2.5 px-3 text-xs font-bold transition-colors cursor-pointer ${
                  mode === "OFFLINE"
                    ? "border-sky-500 bg-sky-50 text-sky-800"
                    : "border-stone-200 bg-white text-neutral-600 hover:bg-stone-50"
                }`}
              >
                <MapPin className="size-4" />
                Trực tiếp (Offline)
              </button>
            </div>
          </div>

          {/* Link hoặc Địa điểm */}
          <div>
            <label htmlFor="place-or-link" className="block text-xs font-bold text-neutral-700 mb-1">
              {mode === "ONLINE" ? "Liên kết phòng học (Google Meet / Zoom / MS Teams)" : "Địa điểm học tập"}
            </label>
            <input
              id="place-or-link"
              type="text"
              placeholder={mode === "ONLINE" ? "https://meet.google.com/..." : "Số 123 đường ABC, Quận XYZ..."}
              value={placeOrLink}
              onChange={(e) => setPlaceOrLink(e.target.value)}
              className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-xs text-neutral-800 focus:border-sky-500 focus:outline-hidden"
              maxLength={255}
            />
          </div>

          {/* Lời nhắn */}
          <div>
            <label htmlFor="session-message" className="block text-xs font-bold text-neutral-700 mb-1">
              Lời nhắn gửi đối phương (Tùy chọn)
            </label>
            <textarea
              id="session-message"
              rows={2}
              placeholder="Chuẩn bị bài tập số 3 trước khi vào học nhé..."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-xs text-neutral-800 focus:border-sky-500 focus:outline-hidden"
              maxLength={500}
            />
          </div>

          {/* Action buttons */}
          <div className="flex items-center justify-end gap-2 pt-3 border-t border-stone-100">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="h-10 px-4 rounded-xl border border-stone-200 bg-white text-xs font-bold text-neutral-700 hover:bg-stone-50 disabled:opacity-50 cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading || connectedClasses.length === 0}
              className="h-10 px-5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold shadow-xs flex items-center gap-2 disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
            >
              {loading ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  Đang gửi đề xuất...
                </>
              ) : (
                "Gửi đề xuất lịch"
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
