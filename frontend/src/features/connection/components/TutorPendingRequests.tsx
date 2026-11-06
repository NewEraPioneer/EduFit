"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  Calendar,
  Clock,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Inbox,
  RefreshCw,
  User,
  GraduationCap,
  MessageSquare,
  DollarSign,
  MapPin,
  X,
} from "lucide-react";
import { connectionApi } from "../api/connectionApi";
import type { TutorInboxItem } from "../types";
import { RespondRequestModal } from "./RespondRequestModal";
import {
  StudentPedagogicalProfileModal,
  inboxNeedDetails,
  inboxNeedPlace,
  inboxNeedTitle,
} from "./StudentPedagogicalProfileModal";
import { FitChecks } from "@/features/discovery/components/FitChecks";
import { formatMoney } from "@/features/learning-needs/catalogRules";
import { useDeepLinkParams } from "@/shared/hooks";

export function TutorPendingRequests() {
  const { requestId } = useDeepLinkParams(["requestId"]);
  const [items, setItems] = useState<TutorInboxItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"ALL" | "PENDING" | "PROCESSED">("PENDING");

  // Modal State
  const [selectedRequest, setSelectedRequest] = useState<TutorInboxItem | null>(null);
  const [modalMode, setModalMode] = useState<"APPROVE" | "REJECT">("APPROVE");
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Profile Modal State
  const [profileModalRequest, setProfileModalRequest] = useState<TutorInboxItem | null>(null);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);

  // Toast State
  const [toast, setToast] = useState<{ message: string; type: "success" | "error" } | null>(null);

  const fetchInbox = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await connectionApi.getTutorInbox();
      setItems(data);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Không thể tải danh sách yêu cầu. Vui lòng thử lại."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchInbox();
  }, [fetchInbox]);

  const handleOpenModal = (request: TutorInboxItem, mode: "APPROVE" | "REJECT") => {
    setSelectedRequest(request);
    setModalMode(mode);
    setIsModalOpen(true);
  };

  const handleOpenProfileModal = (request: TutorInboxItem) => {
    setProfileModalRequest(request);
    setIsProfileModalOpen(true);
  };

  const handleModalSuccess = (classId?: string) => {
    if (modalMode === "APPROVE") {
      setToast({
        message: classId
          ? "Đã chấp thuận yêu cầu và tạo lớp học thành công!"
          : "Đã chấp thuận yêu cầu kết nối!",
        type: "success",
      });
    } else {
      setToast({
        message: "Đã từ chối yêu cầu kết nối.",
        type: "success",
      });
    }
    void fetchInbox();
  };

  // Filter items
  const filteredItems = items.filter((item) => {
    if (filter === "PENDING") return item.status === "PENDING";
    if (filter === "PROCESSED") return item.status !== "PENDING";
    return true;
  });

  const pendingCount = items.filter((i) => i.status === "PENDING").length;

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {toast && (
        <div
          data-testid="toast-notification"
          className={`fixed bottom-5 right-5 z-50 flex items-center justify-between gap-3 rounded-2xl p-4 text-xs font-semibold text-white shadow-xl backdrop-blur-md transition-all ${
            toast.type === "success"
              ? "bg-emerald-900/95 border border-emerald-700/60"
              : "bg-rose-900/95 border border-rose-700/60"
          }`}
        >
          <div className="flex items-center gap-2.5">
            {toast.type === "success" ? (
              <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            ) : (
              <XCircle className="h-4 w-4 text-rose-400 shrink-0" />
            )}
            <span>{toast.message}</span>
          </div>
          <button
            type="button"
            onClick={() => setToast(null)}
            className="p-1 rounded-lg hover:bg-white/20 transition-colors"
            aria-label="Đóng thông báo"
          >
            <X className="h-3.5 w-3.5 opacity-80" />
          </button>
        </div>
      )}

      {/* Header & Filter Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-200 dark:border-zinc-800 pb-5">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-zinc-900 dark:text-zinc-100 flex items-center gap-2.5">
            <Inbox className="h-6 w-6 text-blue-600 dark:text-blue-400" />
            Hộp thư Yêu cầu Kết nối
            {pendingCount > 0 && (
              <span className="inline-flex items-center rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-950/60 dark:text-blue-300">
                {pendingCount} chờ xử lý
              </span>
            )}
          </h2>
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1">
            Xem và xử lý các yêu cầu học kèm từ học sinh và phụ huynh gửi đến bạn.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="inline-flex rounded-xl bg-zinc-100 p-1 dark:bg-zinc-800/80">
            <button
              onClick={() => setFilter("PENDING")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "PENDING"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-700 dark:text-zinc-100"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
              }`}
            >
              Chờ xử lý ({pendingCount})
            </button>
            <button
              onClick={() => setFilter("ALL")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "ALL"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-700 dark:text-zinc-100"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
              }`}
            >
              Tất cả ({items.length})
            </button>
          </div>

          <button
            onClick={() => void fetchInbox()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs font-medium text-zinc-700 shadow-xs hover:bg-zinc-50 active:scale-95 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700"
            title="Làm mới danh sách"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            Làm mới
          </button>
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50/70 p-4 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/20 dark:text-rose-300 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <XCircle className="h-5 w-5 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={() => void fetchInbox()}
            className="rounded-lg bg-rose-100 px-3 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-200 dark:bg-rose-900/40 dark:text-rose-200"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Loading State */}
      {loading && (
        <div className="grid gap-4 md:grid-cols-2">
          {[1, 2, 3, 4].map((i) => (
            <div
              key={i}
              className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-xs animate-pulse dark:border-zinc-800 dark:bg-zinc-900 space-y-4"
            >
              <div className="flex items-center justify-between">
                <div className="h-4 w-32 rounded-md bg-zinc-200 dark:bg-zinc-800" />
                <div className="h-6 w-20 rounded-full bg-zinc-200 dark:bg-zinc-800" />
              </div>
              <div className="space-y-2">
                <div className="h-3 w-48 rounded-md bg-zinc-200 dark:bg-zinc-800" />
                <div className="h-3 w-36 rounded-md bg-zinc-200 dark:bg-zinc-800" />
              </div>
              <div className="h-10 w-full rounded-xl bg-zinc-200 dark:bg-zinc-800" />
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && filteredItems.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-zinc-300 bg-zinc-50/50 py-14 text-center dark:border-zinc-800 dark:bg-zinc-900/50">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-zinc-100 text-zinc-400 dark:bg-zinc-800 dark:text-zinc-500 mb-3">
            <Inbox className="h-6 w-6" />
          </div>
          <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            {filter === "PENDING"
              ? "Chưa có yêu cầu kết nối nào đang chờ"
              : "Không tìm thấy yêu cầu kết nối nào"}
          </h3>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400 max-w-sm">
            {filter === "PENDING"
              ? "Khi có học sinh hoặc phụ huynh gửi lời mời học kèm, thông tin yêu cầu sẽ hiển thị tại đây để bạn duyệt hoặc từ chối."
              : "Danh sách hiện tại trống. Bạn có thể chuyển sang bộ lọc khác để kiểm tra."}
          </p>
        </div>
      )}

      {/* Populated State */}
      {!loading && !error && filteredItems.length > 0 && (
        <div className="grid gap-5 md:grid-cols-2">
          {filteredItems.map((item) => {
            const isPending = item.status === "PENDING";
            const isAccepted = item.status === "ACCEPTED";
            const isRejected = item.status === "REJECTED";

            const isHighlighted = item.requestId === requestId;

            return (
              <div
                key={item.requestId}
                data-testid={`request-card-${item.requestId}`}
                className={`relative flex flex-col justify-between rounded-2xl border p-5 shadow-xs transition-all ${
                  isHighlighted
                    ? "ring-2 ring-blue-500 bg-blue-50/40 border-blue-400"
                    : item.hasSlotConflict && isPending
                      ? "border-amber-300 bg-amber-50/20 dark:border-amber-900/60 dark:bg-amber-950/10"
                      : "border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900"
                }`}
              >
                <div>
                  {/* Top Header: Student Info & Status */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-start gap-3 min-w-0">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-100 text-blue-700 dark:bg-blue-950/70 dark:text-blue-300 font-bold text-sm">
                        {item.studentDisplayName
                          ? item.studentDisplayName.charAt(0).toUpperCase()
                          : "HS"}
                      </div>
                      <div className="min-w-0">
                        <h4 className="font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                          {item.studentDisplayName || "Học sinh ẩn danh"}
                          <span className="text-xs font-normal text-zinc-400">
                            ({item.senderRole === "PARENT" ? "Phụ huynh gửi" : "Học sinh"})
                          </span>
                        </h4>
                        <div className="flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                          <GraduationCap className="h-3.5 w-3.5 text-zinc-400" />
                          <span className="break-words">{inboxNeedTitle(item.need)}</span>
                        </div>
                        {inboxNeedDetails(item.need).length > 0 && (
                          <p className="mt-1 text-[11px] text-zinc-500 dark:text-zinc-400 break-words">
                            {inboxNeedDetails(item.need).join(" · ")}
                          </p>
                        )}
                        {item.fitChecks.length > 0 && <FitChecks checks={item.fitChecks} className="mt-2" />}
                      </div>
                    </div>

                    {/* Status Badge */}
                    <div>
                      {isPending && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2.5 py-1 text-xs font-medium text-blue-800 dark:bg-blue-950/60 dark:text-blue-300">
                          <Clock className="h-3 w-3" />
                          Chờ phản hồi
                        </span>
                      )}
                      {isAccepted && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-medium text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300">
                          <CheckCircle2 className="h-3 w-3" />
                          Đã chấp thuận
                        </span>
                      )}
                      {isRejected && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-zinc-100 px-2.5 py-1 text-xs font-medium text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                          <XCircle className="h-3 w-3" />
                          Đã từ chối
                        </span>
                      )}
                      {!isPending && !isAccepted && !isRejected && (
                        <span className="inline-flex items-center rounded-full bg-zinc-100 px-2.5 py-1 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
                          {item.status}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Slot Conflict Alert Banner */}
                  {item.hasSlotConflict && isPending && (
                    <div
                      data-testid="conflict-warning"
                      className="mt-3.5 rounded-xl border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300 flex items-start gap-2"
                    >
                      <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
                      <div>
                        <span className="font-semibold">Cảnh báo trùng khung giờ:</span> Lịch bận
                        trùng với khung giờ yêu cầu:{" "}
                        <span className="font-medium underline">
                          {item.conflictingSlots?.join(", ") || "Khung giờ đã bận"}
                        </span>
                      </div>
                    </div>
                  )}

                  {/* Details: Slots, Area, Budget */}
                  <div className="mt-4 space-y-2.5 text-xs text-zinc-600 dark:text-zinc-300">
                    {/* Desired Slots */}
                    <div>
                      <span className="font-medium text-zinc-700 dark:text-zinc-200">
                        Khung giờ mong muốn:
                      </span>
                      <div className="mt-1 flex flex-wrap gap-1.5">
                        {item.desiredSlots && item.desiredSlots.length > 0 ? (
                          item.desiredSlots.map((slot, idx) => {
                            const isConflict =
                              item.hasSlotConflict &&
                              item.conflictingSlots?.includes(slot);
                            return (
                              <span
                                key={idx}
                                className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1 font-mono text-[11px] ${
                                  isConflict
                                    ? "border border-amber-300 bg-amber-100 text-amber-900 dark:border-amber-700 dark:bg-amber-950/50 dark:text-amber-300"
                                    : "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
                                }`}
                              >
                                <Calendar className="h-3 w-3 opacity-70" />
                                {slot}
                                {isConflict && (
                                  <AlertTriangle className="h-3 w-3 text-amber-600 dark:text-amber-400" />
                                )}
                              </span>
                            );
                          })
                        ) : (
                          <span className="italic text-zinc-400">Chưa chỉ định khung giờ</span>
                        )}
                      </div>
                    </div>

                    {/* Location & Budget */}
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-zinc-500 dark:text-zinc-400 pt-1">
                      <div className="flex items-center gap-1 min-w-0">
                        <MapPin className="h-3.5 w-3.5 text-zinc-400 shrink-0" />
                        <span className="break-words">{inboxNeedPlace(item.need)}</span>
                      </div>
                      {item.need.budgetMaxPerSession != null && (
                        <div className="flex items-center gap-1">
                          <DollarSign className="h-3.5 w-3.5 text-zinc-400" />
                          <span>Ngân sách tối đa: {formatMoney(item.need.budgetMaxPerSession)}/buổi</span>
                        </div>
                      )}
                    </div>

                    {/* Message */}
                    {item.message && (
                      <div className="rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/60 border border-zinc-100 dark:border-zinc-800 mt-2">
                        <div className="flex items-center gap-1.5 text-zinc-500 dark:text-zinc-400 font-medium mb-1">
                          <MessageSquare className="h-3 w-3" />
                          <span>Lời nhắn:</span>
                        </div>
                        <p className="text-zinc-700 dark:text-zinc-300 italic line-clamp-3">
                          &quot;{item.message}&quot;
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Card Actions */}
                <div className="mt-5 flex items-center justify-between gap-2.5 pt-3 border-t border-zinc-100 dark:border-zinc-800">
                  <button
                    type="button"
                    onClick={() => handleOpenProfileModal(item)}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-indigo-200 bg-indigo-50/60 px-3.5 py-2 text-xs font-semibold text-indigo-700 hover:bg-indigo-100 dark:border-indigo-800 dark:bg-indigo-950/40 dark:text-indigo-300 transition-colors cursor-pointer"
                  >
                    Xem hồ sơ chi tiết
                  </button>
                  {isPending && (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleOpenModal(item, "REJECT")}
                        className="rounded-xl border border-zinc-300 px-3.5 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 hover:border-rose-300 dark:border-zinc-700 dark:text-rose-400 dark:hover:bg-rose-950/30 transition-colors cursor-pointer"
                      >
                        Từ chối
                      </button>
                      <button
                        onClick={() => handleOpenModal(item, "APPROVE")}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 active:scale-95 transition-all cursor-pointer"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        Chấp thuận tạo lớp
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Respond Request */}
      <RespondRequestModal
        request={selectedRequest}
        mode={modalMode}
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={handleModalSuccess}
      />

      {/* Modal Student Pedagogical Profile */}
      <StudentPedagogicalProfileModal
        request={profileModalRequest}
        isOpen={isProfileModalOpen}
        onClose={() => setIsProfileModalOpen(false)}
      />
    </div>
  );
}
