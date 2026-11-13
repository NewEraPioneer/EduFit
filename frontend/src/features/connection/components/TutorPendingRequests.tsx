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
          className={`fixed bottom-5 right-5 z-50 flex items-center justify-between gap-3 rounded-2xl p-4 text-xs font-medium text-white shadow-xl backdrop-blur-md transition-all ${
            toast.type === "success"
              ? "bg-success/95 border border-success/60"
              : "bg-danger/95 border border-danger/60"
          }`}
        >
          <div className="flex items-center gap-2.5">
            {toast.type === "success" ? (
              <CheckCircle2 className="h-4 w-4 text-success-strong shrink-0" />
            ) : (
              <XCircle className="h-4 w-4 text-danger-strong shrink-0" />
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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-line pb-5">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-ink flex items-center gap-2.5">
            <Inbox className="h-6 w-6 text-brand-strong" />
            Hộp thư Yêu cầu Kết nối
            {pendingCount > 0 && (
              <span className="inline-flex items-center rounded-full bg-brand-soft px-2.5 py-0.5 text-xs font-medium text-brand-strong">
                {pendingCount} chờ xử lý
              </span>
            )}
          </h2>
          <p className="text-sm text-muted mt-1">
            Xem và xử lý các yêu cầu học kèm từ học sinh và phụ huynh gửi đến bạn.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="inline-flex rounded-xl bg-sunken p-1">
            <button
              onClick={() => setFilter("PENDING")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "PENDING"
                  ? "bg-surface text-ink shadow-xs"
                  : "text-ink-2 hover:text-ink"
              }`}
            >
              Chờ xử lý ({pendingCount})
            </button>
            <button
              onClick={() => setFilter("ALL")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "ALL"
                  ? "bg-surface text-ink shadow-xs"
                  : "text-ink-2 hover:text-ink"
              }`}
            >
              Tất cả ({items.length})
            </button>
          </div>

          <button
            onClick={() => void fetchInbox()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface px-3 py-1.5 text-xs font-medium text-ink-2 shadow-xs hover:bg-paper active:scale-95 disabled:opacity-50"
            title="Làm mới danh sách"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            Làm mới
          </button>
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="rounded-2xl border border-danger-line bg-danger-soft/70 p-4 text-sm text-danger-strong flex items-center justify-between">
          <div className="flex items-center gap-3">
            <XCircle className="h-5 w-5 text-danger-strong shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={() => void fetchInbox()}
            className="rounded-lg bg-danger-soft px-3 py-1 text-xs font-medium text-danger-strong hover:bg-danger-line"
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
              className="rounded-2xl border border-line bg-surface p-5 shadow-xs animate-pulse space-y-4"
            >
              <div className="flex items-center justify-between">
                <div className="h-4 w-32 rounded-md bg-line" />
                <div className="h-6 w-20 rounded-full bg-line" />
              </div>
              <div className="space-y-2">
                <div className="h-3 w-48 rounded-md bg-line" />
                <div className="h-3 w-36 rounded-md bg-line" />
              </div>
              <div className="h-10 w-full rounded-xl bg-line" />
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && filteredItems.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-line-strong bg-paper/50 py-14 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-sunken text-muted mb-3">
            <Inbox className="h-6 w-6" />
          </div>
          <h3 className="text-base font-medium text-ink">
            {filter === "PENDING"
              ? "Chưa có yêu cầu kết nối nào đang chờ"
              : "Không tìm thấy yêu cầu kết nối nào"}
          </h3>
          <p className="mt-1 text-sm text-muted max-w-sm">
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
                    ? "ring-2 ring-brand bg-brand-soft/40 border-brand"
                    : item.hasSlotConflict && isPending
                      ? "border-action-line bg-action-soft/20"
                      : "border-line bg-surface"
                }`}
              >
                <div>
                  {/* Top Header: Student Info & Status */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-start gap-3 min-w-0">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-brand-strong font-medium text-sm">
                        {item.studentDisplayName
                          ? item.studentDisplayName.charAt(0).toUpperCase()
                          : "HS"}
                      </div>
                      <div className="min-w-0">
                        <h4 className="font-medium text-ink flex items-center gap-2">
                          {item.studentDisplayName || "Học sinh ẩn danh"}
                          <span className="text-xs font-normal text-muted">
                            ({item.senderRole === "PARENT" ? "Phụ huynh gửi" : "Học sinh"})
                          </span>
                        </h4>
                        <div className="flex items-center gap-2 text-xs text-muted mt-0.5">
                          <GraduationCap className="h-3.5 w-3.5 text-muted" />
                          <span className="break-words">{inboxNeedTitle(item.need)}</span>
                        </div>
                        {inboxNeedDetails(item.need).length > 0 && (
                          <p className="mt-1 text-xs text-muted break-words">
                            {inboxNeedDetails(item.need).join(" · ")}
                          </p>
                        )}
                        {item.fitChecks.length > 0 && <FitChecks checks={item.fitChecks} className="mt-2" />}
                      </div>
                    </div>

                    {/* Status Badge */}
                    <div>
                      {isPending && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-brand-soft px-2.5 py-1 text-xs font-medium text-brand-strong">
                          <Clock className="h-3 w-3" />
                          Chờ phản hồi
                        </span>
                      )}
                      {isAccepted && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-success-soft px-2.5 py-1 text-xs font-medium text-success-strong">
                          <CheckCircle2 className="h-3 w-3" />
                          Đã chấp thuận
                        </span>
                      )}
                      {isRejected && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-sunken px-2.5 py-1 text-xs font-medium text-ink-2">
                          <XCircle className="h-3 w-3" />
                          Đã từ chối
                        </span>
                      )}
                      {!isPending && !isAccepted && !isRejected && (
                        <span className="inline-flex items-center rounded-full bg-sunken px-2.5 py-1 text-xs font-medium text-ink-2">
                          {item.status}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Slot Conflict Alert Banner */}
                  {item.hasSlotConflict && isPending && (
                    <div
                      data-testid="conflict-warning"
                      className="mt-3.5 rounded-xl border border-action-line bg-action-soft p-2.5 text-xs text-action-strong flex items-start gap-2"
                    >
                      <AlertTriangle className="h-4 w-4 text-action-strong shrink-0 mt-0.5" />
                      <div>
                        <span className="font-medium">Cảnh báo trùng khung giờ:</span> Lịch bận
                        trùng với khung giờ yêu cầu:{" "}
                        <span className="font-medium underline">
                          {item.conflictingSlots?.join(", ") || "Khung giờ đã bận"}
                        </span>
                      </div>
                    </div>
                  )}

                  {/* Details: Slots, Area, Budget */}
                  <div className="mt-4 space-y-2.5 text-xs text-ink-2">
                    {/* Desired Slots */}
                    <div>
                      <span className="font-medium text-ink-2">
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
                                className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1 font-mono text-xs ${
                                  isConflict
                                    ? "border border-action-line bg-action-soft text-action-strong"
                                    : "bg-sunken text-ink-2"
                                }`}
                              >
                                <Calendar className="h-3 w-3 opacity-70" />
                                {slot}
                                {isConflict && (
                                  <AlertTriangle className="h-3 w-3 text-action-strong" />
                                )}
                              </span>
                            );
                          })
                        ) : (
                          <span className="italic text-muted">Chưa chỉ định khung giờ</span>
                        )}
                      </div>
                    </div>

                    {/* Location & Budget */}
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted pt-1">
                      <div className="flex items-center gap-1 min-w-0">
                        <MapPin className="h-3.5 w-3.5 text-muted shrink-0" />
                        <span className="break-words">{inboxNeedPlace(item.need)}</span>
                      </div>
                      {item.need.budgetMaxPerSession != null && (
                        <div className="flex items-center gap-1">
                          <DollarSign className="h-3.5 w-3.5 text-muted" />
                          <span>Ngân sách tối đa: {formatMoney(item.need.budgetMaxPerSession)}/buổi</span>
                        </div>
                      )}
                    </div>

                    {/* Message */}
                    {item.message && (
                      <div className="rounded-xl bg-paper p-3 border border-line mt-2">
                        <div className="flex items-center gap-1.5 text-muted font-medium mb-1">
                          <MessageSquare className="h-3 w-3" />
                          <span>Lời nhắn:</span>
                        </div>
                        <p className="text-ink-2 italic line-clamp-3">
                          &quot;{item.message}&quot;
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Card Actions */}
                <div className="mt-5 flex items-center justify-between gap-2.5 pt-3 border-t border-line">
                  <button
                    type="button"
                    onClick={() => handleOpenProfileModal(item)}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-brand-line bg-brand-soft/60 px-3.5 py-2 text-xs font-medium text-brand-strong hover:bg-brand-soft transition-colors cursor-pointer"
                  >
                    Xem hồ sơ chi tiết
                  </button>
                  {isPending && (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleOpenModal(item, "REJECT")}
                        className="rounded-xl border border-line-strong px-3.5 py-2 text-xs font-medium text-danger-strong hover:bg-danger-soft hover:border-danger-line transition-colors cursor-pointer"
                      >
                        Từ chối
                      </button>
                      <button
                        onClick={() => handleOpenModal(item, "APPROVE")}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-success px-4 py-2 text-xs font-medium text-white shadow-xs hover:bg-success-deep active:scale-95 transition-all cursor-pointer"
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
