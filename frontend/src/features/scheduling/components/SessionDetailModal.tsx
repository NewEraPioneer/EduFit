"use client";

import { DateField } from "@/shared/ui/DateField";
import { TimeField } from "@/shared/ui/TimeField";
import { SessionBillingPanel } from "@/features/payment/components/SessionBillingPanel";
import React, { useState, useEffect, useRef } from "react";
import {
  X,
  Clock,
  MapPin,
  Video,
  CheckCircle2,
  XCircle,
  CalendarClock,
  AlertTriangle,
  RotateCcw,
  Ban,
  MessageSquare,
  Loader2,
  ExternalLink,
  FileText,
  BookOpen,
} from "lucide-react";
import { schedulingApi, isConflictError, getConflictMessage } from "../api/schedulingApi";
import { rescheduleSessionSchema, cancelSessionSchema } from "../schemas/schedulingSchemas";
import {
  type SessionDetail,
  type CancelReason,
  type RescheduleProposal,
  type ConnectedClass,
  formatConnectedClassName,
} from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";
import { formatLocalDateToIso, formatLocalTime } from "@/shared/utils/dateTime";
import { PlannedMilestonesPicker, SessionOutcomeModal, progressApi, type PlanResponse } from "@/features/progress";

export const canRecordSessionOutcome = (status?: string, role?: string): boolean =>
  status === "SCHEDULED" && role === "TUTOR";

export interface SessionDetailModalProps {
  sessionId: string | null;
  isOpen: boolean;
  onClose: () => void;
  onUpdated: () => void;
  role: UserRole;
  currentUserId?: string;
  classes?: ConnectedClass[];
  initialAction?: "outcome" | null;
  /** Gọi khi tải xong chi tiết buổi học (vd. để lịch nhảy tới đúng tuần) */
  onSessionLoaded?: (session: SessionDetail) => void;
}

export function SessionDetailModal({
  sessionId,
  isOpen,
  onClose,
  onUpdated,
  role,
  currentUserId,
  classes = [],
  initialAction,
  onSessionLoaded,
}: SessionDetailModalProps) {
  const onSessionLoadedRef = useRef(onSessionLoaded);
  useEffect(() => {
    onSessionLoadedRef.current = onSessionLoaded;
  });
  const [session, setSession] = useState<SessionDetail | null>(null);
  const [rescheduleProposal, setRescheduleProposal] = useState<RescheduleProposal | null>(null);
  const [showRescheduleRejectInput, setShowRescheduleRejectInput] = useState(false);
  const [rescheduleRejectReason, setRescheduleRejectReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  // Đồng hồ cho nút ghi nhận kết quả (chỉ mở sau giờ kết thúc); không gọi Date.now() khi render.
  const [clockMs, setClockMs] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setClockMs(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);

  const classMap = React.useMemo(() => {
    return new Map<string, ConnectedClass>((classes || []).map((c) => [c.classId, c]));
  }, [classes]);

  const currentClass = session?.classId ? classMap.get(session.classId) : null;
  const currentClassName = session?.classId
    ? formatConnectedClassName(currentClass, role, session.classId)
    : "Lớp học";

  // Subview toggles for actions
  const [showRescheduleForm, setShowRescheduleForm] = useState(false);
  const [showCancelForm, setShowCancelForm] = useState(false);
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [isWithin24Hours, setIsWithin24Hours] = useState(false);

  // Reschedule form states
  const [newDateStr, setNewDateStr] = useState("");
  const [newStartTimeStr, setNewStartTimeStr] = useState("08:00");
  const [newEndTimeStr, setNewEndTimeStr] = useState("09:00");
  const [rescheduleReason, setRescheduleReason] = useState("");

  // Cancel form states
  const [cancelReason, setCancelReason] = useState<CancelReason>("SCHEDULE_CONFLICT");
  const [cancelComment, setCancelComment] = useState("");

  // Reject form state
  const [rejectReason, setRejectReason] = useState("");
  const [showOutcomeModal, setShowOutcomeModal] = useState(false);
  const [classPlan, setClassPlan] = useState<PlanResponse | null>(null);
  const [plannedMilestoneIds, setPlannedMilestoneIds] = useState<string[]>([]);
  const handledActionRef = useRef<string | null>(null);

  // Track session ID during render to avoid cascading renders in useEffect (React 19 pattern)
  const [prevSessionId, setPrevSessionId] = useState<string | null>(null);
  if (isOpen && sessionId !== prevSessionId) {
    setPrevSessionId(sessionId);
    setRescheduleProposal(null);
    setShowRescheduleRejectInput(false);
    setRescheduleRejectReason("");
    setShowOutcomeModal(false);
  } else if (!isOpen && prevSessionId !== null) {
    setPrevSessionId(null);
    setSession(null);
    setRescheduleProposal(null);
    setShowRescheduleRejectInput(false);
    setRescheduleRejectReason("");
    setShowOutcomeModal(false);
  }

  useEffect(() => {
    if (!isOpen) {
      handledActionRef.current = null;
    }
  }, [isOpen]);

  // Fetch session details on mount / id change
  useEffect(() => {
    let ignore = false;
    if (isOpen && sessionId) {
      schedulingApi
        .getSessionDetail(sessionId)
        .then((res) => {
          if (!ignore) {
            if (res.success && res.data) {
              setSession(res.data);
              onSessionLoadedRef.current?.(res.data);
              const canRecord = canRecordSessionOutcome(res.data.status, role);
              const actionKey = `${sessionId}:${initialAction}`;
              if (
                canRecord &&
                initialAction === "outcome" &&
                handledActionRef.current !== actionKey
              ) {
                handledActionRef.current = actionKey;
                setShowOutcomeModal(true);
              }
              const start = new Date(res.data.startAt);
              const end = new Date(res.data.endAt);
              if (!Number.isNaN(start.getTime())) {
                setNewDateStr(formatLocalDateToIso(start));
                setNewStartTimeStr(formatLocalTime(start));
              }
              if (!Number.isNaN(end.getTime())) {
                setNewEndTimeStr(formatLocalTime(end));
              }
              if ((res.data as unknown as { rescheduleProposal?: RescheduleProposal }).rescheduleProposal) {
                setRescheduleProposal(
                  (res.data as unknown as { rescheduleProposal: RescheduleProposal }).rescheduleProposal
                );
              } else {
                schedulingApi
                  .getRescheduleProposal(sessionId)
                  .then((pRes) => {
                    if (!ignore && pRes.success && pRes.data) {
                      setRescheduleProposal(pRes.data);
                    }
                  })
                  .catch(() => {});
              }
              if (res.data.classId) {
                progressApi
                  .getPlanByClassId(res.data.classId)
                  .then((pRes) => {
                    if (!ignore && pRes.success && pRes.data) {
                      setClassPlan(pRes.data);
                    }
                  })
                  .catch(() => {});
              }
            } else {
              setErrorMessage(res.message || "Không thể tải thông tin buổi học.");
            }
            setLoading(false);
          }
        })
        .catch((err) => {
          if (!ignore) {
            setErrorMessage(err instanceof Error ? err.message : "Lỗi kết nối máy chủ");
            setLoading(false);
          }
        });
    }
    return () => {
      ignore = true;
    };
  }, [isOpen, sessionId, initialAction, role]);

  if (!isOpen || !sessionId) return null;

  // Format date helper
  const formatDateTime = (iso: string) => {
    try {
      const d = new Date(iso);
      return new Intl.DateTimeFormat("vi-VN", {
        weekday: "short",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(d);
    } catch {
      return iso;
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "PROPOSED":
        return { text: "Chờ xác nhận", bg: "bg-action-soft text-action-strong border-action-line" };
      case "SCHEDULED":
        return { text: "Đã xếp lịch", bg: "bg-brand-soft text-brand-strong border-brand-line" };
      case "COMPLETED":
        return { text: "Đã hoàn thành", bg: "bg-success-soft text-success-strong border-success-line" };
      case "CANCELLED":
        return { text: "Đã hủy", bg: "bg-danger-soft text-danger-strong border-danger-line" };
      case "REJECTED":
        return { text: "Từ chối", bg: "bg-line text-ink-2 border-line-strong" };
      case "EXPIRED":
        return { text: "Hết hạn", bg: "bg-line text-ink-2 border-line-strong" };
      default:
        return { text: status, bg: "bg-sunken text-ink-2 border-line" };
    }
  };

  // Open cancel form and compute late cancel in event handler (12 hours according to BR-46)
  const handleOpenCancelForm = () => {
    if (session) {
      const isLate = new Date(session.startAt).getTime() - Date.now() < 12 * 3600 * 1000;
      setIsWithin24Hours(isLate);
    }
    setShowCancelForm(true);
  };

  // Handle Respond to Reschedule (UC4.4)
  const handleRespondReschedule = async (accept: boolean) => {
    if (!rescheduleProposal) return;
    setErrorMessage(null);
    setIsConflict(false);
    setActionLoading(true);

    try {
      const res = await schedulingApi.respondToReschedule(rescheduleProposal.proposalId, {
        accept,
        responseReason:
          !accept && rescheduleRejectReason.trim() ? rescheduleRejectReason.trim() : undefined,
      });
      if (res.success) {
        setRescheduleProposal(null);
        setShowRescheduleRejectInput(false);
        setRescheduleRejectReason("");
        onUpdated();
        onClose();
      } else {
        setErrorMessage(res.message || "Không thể phản hồi yêu cầu đổi lịch.");
      }
    } catch (err: unknown) {
      if (isConflictError(err)) {
        setIsConflict(true);
        setErrorMessage(
          getConflictMessage(
            err,
            "Khung giờ dời lịch đã bị trùng với lịch khác! Vui lòng chọn thời gian khác."
          )
        );
      } else {
        setErrorMessage(
          err instanceof Error ? err.message : "Đã xảy ra lỗi khi phản hồi yêu cầu đổi lịch."
        );
      }
    } finally {
      setActionLoading(false);
    }
  };


  // Handle Accept proposal
  const handleAcceptProposal = async () => {
    if (!session) return;
    setErrorMessage(null);
    setIsConflict(false);
    setActionLoading(true);

    try {
      const res = await schedulingApi.respondToProposal(session.sessionId, { accept: true });
      if (res.success) {
        onUpdated();
        onClose();
      } else {
        setErrorMessage(res.message || "Không thể chấp nhận buổi học.");
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
      } else {
        setErrorMessage(err instanceof Error ? err.message : "Đã xảy ra lỗi khi xác nhận lịch học.");
      }
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Reject proposal
  const handleRejectProposal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!session) return;
    setErrorMessage(null);
    setIsConflict(false);
    setActionLoading(true);

    try {
      const res = await schedulingApi.respondToProposal(session.sessionId, {
        accept: false,
        reason: rejectReason.trim() || undefined,
      });
      if (res.success) {
        onUpdated();
        onClose();
      } else {
        setErrorMessage(res.message || "Không thể từ chối đề xuất.");
      }
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Đã xảy ra lỗi khi từ chối đề xuất.");
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Reschedule
  const handleReschedule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!session) return;
    setErrorMessage(null);
    setIsConflict(false);

    if (!newDateStr || !newStartTimeStr || !newEndTimeStr) {
      setErrorMessage("Chọn ngày và giờ bắt đầu, kết thúc hợp lệ.");
      return;
    }
    const startDateTime = new Date(`${newDateStr}T${newStartTimeStr}:00`);
    let endDateTime = new Date(`${newDateStr}T${newEndTimeStr}:00`);
    if (Number.isNaN(startDateTime.getTime()) || Number.isNaN(endDateTime.getTime())) {
      setErrorMessage("Thời gian không hợp lệ. Vui lòng kiểm tra lại ngày và giờ.");
      return;
    }
    if (endDateTime <= startDateTime) {
      endDateTime = new Date(endDateTime.getTime() + 24 * 3600 * 1000);
    }

    const payload = {
      newStartAt: startDateTime.toISOString(),
      newEndAt: endDateTime.toISOString(),
      reason: rescheduleReason.trim() || undefined,
    };

    const validation = rescheduleSessionSchema.safeParse(payload);
    if (!validation.success) {
      setErrorMessage(validation.error.issues[0]?.message || "Thời gian dời lịch không hợp lệ.");
      return;
    }

    setActionLoading(true);
    try {
      const res = await schedulingApi.rescheduleSession(session.sessionId, payload);
      if (res.success) {
        onUpdated();
        onClose();
      } else {
        setErrorMessage(res.message || "Không thể tạo đề xuất đổi lịch.");
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
      } else {
        setErrorMessage(err instanceof Error ? err.message : "Đã xảy ra lỗi khi đổi lịch.");
      }
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Cancel
  const handleCancelSession = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!session) return;
    setErrorMessage(null);
    setIsConflict(false);

    const payload = {
      reason: cancelReason,
      comment: cancelComment.trim() || undefined,
    };

    const validation = cancelSessionSchema.safeParse(payload);
    if (!validation.success) {
      setErrorMessage(validation.error.issues[0]?.message || "Thông tin hủy không hợp lệ.");
      return;
    }

    setActionLoading(true);
    try {
      const res = await schedulingApi.cancelSession(session.sessionId, payload);
      if (res.success) {
        onUpdated();
        onClose();
      } else {
        setErrorMessage(res.message || "Không thể hủy buổi học.");
      }
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Đã xảy ra lỗi khi hủy buổi học.");
    } finally {
      setActionLoading(false);
    }
  };

  const statusBadge = session ? getStatusBadge(session.status) : { text: "", bg: "" };
  const isAuthor = Boolean(currentUserId && session && session.proposedBy === currentUserId);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="session-modal-title"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs overflow-y-auto"
    >
      <div className="relative w-full max-w-dialog overflow-hidden rounded-3xl bg-surface shadow-2xl border border-line animate-in fade-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-line px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="grid size-9 place-items-center rounded-xl bg-brand-soft text-brand-strong">
              <CalendarClock className="size-5" />
            </div>
            <div>
              <h2 id="session-modal-title" className="text-base font-medium text-ink">
                Chi tiết buổi học
              </h2>
              {session && (
                <div className="flex flex-wrap items-center gap-2 mt-0.5">
                  <span
                    className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${statusBadge.bg}`}
                  >
                    {statusBadge.text}
                  </span>
                  <span className="text-xs font-medium text-brand-strong">
                    {currentClassName}
                  </span>
                </div>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-muted hover:bg-sunken hover:text-ink-2 transition-colors"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 max-h-[80vh] overflow-y-auto">
          {/* Skeleton loading state */}
          {loading && (
            <div className="space-y-4 animate-pulse">
              <div className="h-14 rounded-2xl bg-sunken" />
              <div className="h-10 rounded-xl bg-sunken" />
              <div className="h-20 rounded-2xl bg-sunken" />
            </div>
          )}

          {/* Cảnh báo lỗi / Trùng lịch 409 */}
          {errorMessage && (
            <div
              role="alert"
              className={`flex items-start gap-3 rounded-2xl p-4 text-xs font-medium leading-relaxed ${
                isConflict
                  ? "bg-danger-soft text-danger-strong border border-danger-line"
                  : "bg-action-soft text-action-strong border border-action-line"
              }`}
            >
              <AlertTriangle className={`size-4 shrink-0 mt-0.5 ${isConflict ? "text-danger-strong" : "text-action-strong"}`} />
              <div>
                <p className="font-medium">{isConflict ? "Khung giờ bị trùng lịch!" : "Có lỗi xảy ra"}</p>
                <p className="mt-0.5">{errorMessage}</p>
              </div>
            </div>
          )}

          {session && !loading && (
            <>
              {/* Thẻ thông tin lớp học */}
              <div className="rounded-2xl border border-brand-line bg-brand-soft/60 p-4">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 text-xs font-medium text-brand-strong">
                    <BookOpen className="size-3.5 text-brand-strong" />
                    <span>Lớp học</span>
                  </div>
                  {currentClass?.status && (
                    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-brand-soft text-brand-strong">
                      {currentClass.status === "ACTIVE"
                        ? "Đang hoạt động"
                        : currentClass.status === "ENDED"
                        ? "Đã kết thúc"
                        : currentClass.status}
                    </span>
                  )}
                </div>
                <div className="text-sm font-medium text-ink mt-1">
                  {currentClassName}
                </div>
                {(currentClass?.subjectName ||
                  currentClass?.educationLevelName ||
                  currentClass?.tutorName ||
                  currentClass?.studentName) && (
                  <div className="text-xs text-ink-2 mt-1 flex flex-wrap gap-x-4 gap-y-1">
                    {currentClass.subjectName && (
                      <span>
                        Môn học: <b>{currentClass.subjectName}</b>
                      </span>
                    )}
                    {currentClass.educationLevelName && (
                      <span>
                        Cấp học: <b>{currentClass.educationLevelName}</b>
                      </span>
                    )}
                    {currentClass.tutorName && (
                      <span>
                        Gia sư: <b>{currentClass.tutorName}</b>
                      </span>
                    )}
                    {currentClass.studentName && (
                      <span>
                        Học sinh: <b>{currentClass.studentName}</b>
                      </span>
                    )}
                  </div>
                )}
              </div>
              {/* Thẻ thời gian và hình thức */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="rounded-2xl border border-line bg-paper/70 p-4">
                  <div className="flex items-center gap-2 text-xs font-medium text-muted mb-1">
                    <Clock className="size-3.5" />
                    Thời gian diễn ra
                  </div>
                  <div className="text-xs font-medium text-ink">
                    {formatDateTime(session.startAt)}
                  </div>
                  <div className="text-xs text-muted mt-0.5">
                    Đến: {formatDateTime(session.endAt)}
                  </div>
                </div>

                <div className="rounded-2xl border border-line bg-paper/70 p-4">
                  <div className="flex items-center gap-2 text-xs font-medium text-muted mb-1">
                    {session.mode === "ONLINE" ? <Video className="size-3.5" /> : <MapPin className="size-3.5" />}
                    Hình thức tổ chức
                  </div>
                  <div className="text-xs font-medium text-ink">
                    {session.mode === "ONLINE" ? "Trực tuyến (Online)" : "Trực tiếp (Offline)"}
                  </div>
                  <div className="text-xs text-muted mt-0.5 truncate">
                    {session.mode === "ONLINE" && ["PROPOSED", "SCHEDULED"].includes(session.status) ? (
                      "Phòng học được mở ở phần thanh toán khi đến giờ học."
                    ) : session.placeOrLink ? (
                      session.mode === "ONLINE" ? (
                        <a
                          href={session.placeOrLink}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-brand-strong hover:underline"
                        >
                          Mở liên kết phòng học <ExternalLink className="size-2.5" />
                        </a>
                      ) : (
                        session.placeOrLink
                      )
                    ) : (
                      "Chưa cập nhật link/địa điểm"
                    )}
                  </div>
                </div>
              </div>

              {/* Cột mốc dự kiến/thực tế của buổi (module Progress) */}
              {["PROPOSED", "SCHEDULED", "COMPLETED"].includes(session.status) && (
                <PlannedMilestonesPicker
                  sessionId={session.sessionId}
                  plan={classPlan}
                  editable={role === "TUTOR" && ["PROPOSED", "SCHEDULED"].includes(session.status)}
                  onChange={setPlannedMilestoneIds}
                />
              )}

              {(["STUDENT", "PARENT", "TUTOR"] as string[]).includes(role) && ["SCHEDULED", "COMPLETED", "ABSENT", "CANCELLED"].includes(session.status) && <SessionBillingPanel sessionId={session.sessionId} mode={session.mode} role={role as "STUDENT" | "PARENT" | "TUTOR"} />}

              {/* Banner đề xuất dời lịch học (UC4.4) */}
              {rescheduleProposal && rescheduleProposal.status === "PENDING" && (
                <div
                  role="region"
                  aria-label="Đề xuất đổi lịch học"
                  className="rounded-2xl border border-brand-line bg-brand-soft/90 p-4 text-xs text-brand-strong space-y-3 shadow-xs"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 font-medium text-brand-strong">
                      <RotateCcw className="size-4 text-brand-strong" />
                      <span className="text-sm font-medium">Đề xuất đổi lịch học (UC4.4)</span>
                    </div>
                    <span className="rounded-full bg-brand-line/80 px-2.5 py-0.5 text-xs font-medium text-brand-strong">
                      Chờ phản hồi
                    </span>
                  </div>

                  <div className="space-y-1.5 text-ink">
                    <div>
                      <span className="font-medium text-ink-2">Thời gian đề xuất mới: </span>
                      <span className="font-medium text-brand-strong text-xs">
                        {formatDateTime(rescheduleProposal.newStartAt)} - {formatLocalTime(rescheduleProposal.newEndAt)}
                      </span>
                    </div>
                    {rescheduleProposal.reason && (
                      <div className="flex items-start gap-1">
                        <span className="font-medium text-ink-2 shrink-0">Lý do: </span>
                        <span className="italic text-ink-2">{rescheduleProposal.reason}</span>
                      </div>
                    )}
                  </div>

                  {currentUserId && rescheduleProposal.proposedBy === currentUserId ? (
                    <div className="text-xs font-medium text-brand-strong italic pt-1 border-t border-brand-line/60">
                      Bạn đã gửi đề xuất dời lịch này. Đang chờ đối phương xác nhận.
                    </div>
                  ) : (
                    <div className="pt-2 border-t border-brand-line/60 space-y-2">
                      {!showRescheduleRejectInput ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            disabled={actionLoading}
                            onClick={() => setShowRescheduleRejectInput(true)}
                            className="h-8 px-3.5 rounded-xl border border-line-strong bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer disabled:opacity-50"
                          >
                            Từ chối
                          </button>
                          <button
                            type="button"
                            disabled={actionLoading}
                            onClick={() => void handleRespondReschedule(true)}
                            className="h-8 px-4 rounded-xl bg-success hover:bg-success-deep text-white text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50 shadow-xs"
                          >
                            {actionLoading ? (
                              <Loader2 className="size-3 animate-spin" />
                            ) : (
                              <CheckCircle2 className="size-3" />
                            )}
                            Chấp thuận
                          </button>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          <input
                            type="text"
                            value={rescheduleRejectReason}
                            onChange={(e) => setRescheduleRejectReason(e.target.value)}
                            placeholder="Nhập lý do từ chối (tùy chọn)..."
                            className="w-full rounded-xl border border-line-strong bg-surface px-3 py-1.5 text-xs text-ink focus:outline-brand"
                          />
                          <div className="flex items-center justify-end gap-2">
                            <button
                              type="button"
                              disabled={actionLoading}
                              onClick={() => setShowRescheduleRejectInput(false)}
                              className="h-7 px-2.5 rounded-lg text-xs font-medium text-ink-2 hover:bg-sunken cursor-pointer"
                            >
                              Hủy
                            </button>
                            <button
                              type="button"
                              disabled={actionLoading}
                              onClick={() => void handleRespondReschedule(false)}
                              className="h-7 px-3 rounded-lg bg-danger hover:bg-danger-deep text-white text-xs font-medium cursor-pointer disabled:opacity-50"
                            >
                              {actionLoading ? "Đang xử lý..." : "Xác nhận từ chối"}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {/* Lời nhắn / Ghi chú lặp lại */}
              {(session.repeatNote || session.message) && (
                <div className="rounded-2xl border border-line bg-surface p-4 space-y-2">
                  {session.repeatNote && (
                    <div className="text-xs text-ink-2">
                      <span className="font-medium text-ink">Chu kỳ học:</span> {session.repeatNote}
                    </div>
                  )}
                  {session.message && (
                    <div className="text-xs text-ink-2 flex items-start gap-2">
                      <MessageSquare className="size-3.5 text-muted shrink-0 mt-0.5" />
                      <div>
                        <span className="font-medium text-ink">Lời nhắn:</span> {session.message}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Thông tin hủy (nếu đã hủy) */}
              {session.status === "CANCELLED" && (
                <div className="rounded-2xl border border-danger-line bg-danger-soft/60 p-4 text-xs text-danger-strong space-y-1">
                  <div className="font-medium flex items-center gap-1.5">
                    <Ban className="size-4 text-danger-strong" />
                    Buổi học đã bị hủy
                  </div>
                  {session.cancelReason && (
                    <div>
                      <span className="font-medium">Lý do:</span> {session.cancelReason}
                    </div>
                  )}
                  {session.cancelComment && (
                    <div>
                      <span className="font-medium">Ghi chú:</span> {session.cancelComment}
                    </div>
                  )}
                  {session.isLateCancel && (
                    <div className="font-medium text-danger-strong mt-1">
                      Buổi học này bị hủy sát giờ (&lt; 12 giờ trước giờ học).
                    </div>
                  )}
                </div>
              )}

              {/* Form đổi lịch (Reschedule Form) */}
              {showRescheduleForm && (
                <form
                  onSubmit={handleReschedule}
                  className="rounded-2xl border border-brand-line bg-brand-soft/50 p-4 space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-brand-strong flex items-center gap-1.5">
                      <RotateCcw className="size-3.5 text-brand-strong" />
                      Đề xuất dời lịch học sang thời gian mới
                    </span>
                    <button
                      type="button"
                      onClick={() => setShowRescheduleForm(false)}
                      className="text-xs text-muted hover:text-ink cursor-pointer"
                    >
                      Đóng
                    </button>
                  </div>

                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                    <div>
                      <label className="block text-xs font-medium text-ink-2 mb-1">Ngày mới</label>
                      <DateField
                        aria-label="Ngày mới"
                        value={newDateStr}
                        onChange={(value) => setNewDateStr(value)}
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-ink-2 mb-1">Giờ bắt đầu</label>
                      <TimeField
                        aria-label="Giờ bắt đầu mới"
                        value={newStartTimeStr}
                        onChange={(value) => setNewStartTimeStr(value)}
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-ink-2 mb-1">Giờ kết thúc</label>
                      <TimeField
                        aria-label="Giờ kết thúc mới"
                        value={newEndTimeStr}
                        onChange={(value) => setNewEndTimeStr(value)}
                        required
                        startFrom={newStartTimeStr}
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-ink-2 mb-1">Lý do dời lịch</label>
                    <input
                      type="text"
                      placeholder="Lý do bận việc đột xuất..."
                      value={rescheduleReason}
                      onChange={(e) => setRescheduleReason(e.target.value)}
                      className="w-full rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs text-ink"
                      maxLength={500}
                    />
                  </div>

                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setShowRescheduleForm(false)}
                      className="h-8 px-3 rounded-lg border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer"
                    >
                      Hủy bỏ
                    </button>
                    <button
                      type="submit"
                      disabled={actionLoading}
                      className="h-8 px-3.5 rounded-lg bg-brand hover:bg-brand-deep text-white text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                    >
                      {actionLoading ? <Loader2 className="size-3 animate-spin" /> : <RotateCcw className="size-3" />}
                      Gửi yêu cầu dời lịch
                    </button>
                  </div>
                </form>
              )}

              {/* Form hủy lịch (Cancel Form) */}
              {showCancelForm && (
                <form
                  onSubmit={handleCancelSession}
                  className="rounded-2xl border border-danger-line bg-danger-soft/50 p-4 space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-danger-strong flex items-center gap-1.5">
                      <Ban className="size-3.5 text-danger-strong" />
                      Xác nhận hủy buổi học
                    </span>
                    <button
                      type="button"
                      onClick={() => setShowCancelForm(false)}
                      className="text-xs text-muted hover:text-ink cursor-pointer"
                    >
                      Đóng
                    </button>
                  </div>

                  {isWithin24Hours && (
                    <div className="rounded-xl border border-action-line bg-action-soft p-2.5 text-xs text-action-strong">
                      Buổi học bắt đầu trong vòng 12 giờ. Hệ thống sẽ ghi nhận đây là hủy muộn.
                    </div>
                  )}

                  <div>
                    <label className="block text-xs font-medium text-ink-2 mb-1">
                      Lý do hủy buổi học <span className="text-danger-strong">*</span>
                    </label>
                    <select
                      value={cancelReason}
                      onChange={(e) => setCancelReason(e.target.value as CancelReason)}
                      className="w-full rounded-lg border border-line bg-surface px-2 py-1.5 text-xs font-medium text-ink"
                    >
                      <option value="SCHEDULE_CONFLICT">Trùng lịch đột xuất (Schedule conflict)</option>
                      <option value="PERSONAL_REASON">Lý do cá nhân / sức khỏe (Personal reason)</option>
                      <option value="OTHER_PARTY_UNAVAILABLE">Đối phương không thể tham gia (Counterparty unavailable)</option>
                      <option value="OTHER">Lý do khác (Other)</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-ink-2 mb-1">Ghi chú giải thích</label>
                    <textarea
                      rows={2}
                      placeholder="Giải thích thêm về lý do hủy để đối phương nắm thông tin..."
                      value={cancelComment}
                      onChange={(e) => setCancelComment(e.target.value)}
                      className="w-full rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs text-ink"
                      maxLength={500}
                    />
                  </div>

                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setShowCancelForm(false)}
                      className="h-8 px-3 rounded-lg border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer"
                    >
                      Quay lại
                    </button>
                    <button
                      type="submit"
                      disabled={actionLoading}
                      className="h-8 px-3.5 rounded-lg bg-danger hover:bg-danger-deep text-white text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                    >
                      {actionLoading ? <Loader2 className="size-3 animate-spin" /> : <Ban className="size-3" />}
                      Xác nhận hủy buổi học
                    </button>
                  </div>
                </form>
              )}

              {/* Form từ chối đề xuất (Reject Form) */}
              {showRejectForm && (
                <form
                  onSubmit={handleRejectProposal}
                  className="rounded-2xl border border-line bg-paper p-4 space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-ink">Từ chối đề xuất buổi học</span>
                    <button
                      type="button"
                      onClick={() => setShowRejectForm(false)}
                      className="text-xs text-muted hover:text-ink cursor-pointer"
                    >
                      Đóng
                    </button>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-2 mb-1">Lý do từ chối (Tùy chọn)</label>
                    <input
                      type="text"
                      placeholder="Không phù hợp khung giờ này..."
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      className="w-full rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs text-ink"
                      maxLength={500}
                    />
                  </div>
                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setShowRejectForm(false)}
                      className="h-8 px-3 rounded-lg border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer"
                    >
                      Hủy bỏ
                    </button>
                    <button
                      type="submit"
                      disabled={actionLoading}
                      className="h-8 px-3.5 rounded-lg bg-night hover:bg-night text-white text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                    >
                      {actionLoading ? <Loader2 className="size-3 animate-spin" /> : <XCircle className="size-3" />}
                      Gửi từ chối
                    </button>
                  </div>
                </form>
              )}

              {/* Action Buttons Footer (when forms are closed) */}
              {!showRescheduleForm && !showCancelForm && !showRejectForm && (
                <div className="flex flex-wrap items-center justify-end gap-2 pt-4 border-t border-line">
                  {/* Trạng thái PROPOSED: Xác nhận hoặc Từ chối */}
                  {session.status === "PROPOSED" && (
                    <>
                      {isAuthor ? (
                        <div className="mr-auto text-xs font-medium text-action-strong">
                          Bạn đã gửi đề xuất này. Đang chờ đối phương xác nhận.
                        </div>
                      ) : (
                        <>
                          <button
                            type="button"
                            onClick={() => setShowRejectForm(true)}
                            disabled={actionLoading}
                            className="h-10 px-4 rounded-xl border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer disabled:opacity-50"
                          >
                            Từ chối
                          </button>
                          <button
                            type="button"
                            onClick={handleAcceptProposal}
                            disabled={actionLoading}
                            className="h-10 px-4 rounded-xl bg-success hover:bg-success-deep text-white text-xs font-medium shadow-xs flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                          >
                            {actionLoading ? (
                              <Loader2 className="size-3.5 animate-spin" />
                            ) : (
                              <CheckCircle2 className="size-3.5" />
                            )}
                            Xác nhận lịch học
                          </button>
                        </>
                      )}
                    </>
                  )}

                  {/* Trạng thái SCHEDULED: Đổi lịch hoặc Hủy */}
                  {session.status === "SCHEDULED" && (
                    <>
                      {canRecordSessionOutcome(session.status, role) && (
                        <button
                          type="button"
                          onClick={() => setShowOutcomeModal(true)}
                          disabled={actionLoading || clockMs < new Date(session.endAt).getTime()}
                          title={clockMs < new Date(session.endAt).getTime() ? "Chỉ ghi nhận kết quả sau giờ kết thúc buổi học" : undefined}
                          className="h-10 px-4 rounded-xl border border-success-line bg-success-soft text-success-strong hover:bg-success-soft text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                        >
                          <FileText className="size-3.5" />
                          Ghi nhận biên bản
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => setShowRescheduleForm(true)}
                        disabled={actionLoading}
                        className="h-10 px-4 rounded-xl border border-brand-line bg-brand-soft text-brand-strong hover:bg-brand-soft text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                      >
                        <RotateCcw className="size-3.5" />
                        Đổi lịch học
                      </button>
                      <button
                        type="button"
                        onClick={handleOpenCancelForm}
                        disabled={actionLoading}
                        className="h-10 px-4 rounded-xl border border-danger-line bg-danger-soft text-danger-strong hover:bg-danger-soft text-xs font-medium flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                      >
                        <Ban className="size-3.5" />
                        Hủy buổi học
                      </button>
                    </>
                  )}

                  <button
                    type="button"
                    onClick={onClose}
                    className="h-10 px-4 rounded-xl border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer"
                  >
                    Đóng
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Modal ghi nhận biên bản buổi học */}
      {showOutcomeModal && session && (
        <SessionOutcomeModal
          isOpen={showOutcomeModal}
          onClose={() => setShowOutcomeModal(false)}
          session={{
            sessionId: session.sessionId,
            classId: session.classId,
            startAt: session.startAt,
            endAt: session.endAt,
          }}
          plan={classPlan}
          plannedMilestoneIds={plannedMilestoneIds}
          onSuccess={() => {
            setShowOutcomeModal(false);
            onUpdated();
            onClose();
          }}
        />
      )}
    </div>
  );
}
