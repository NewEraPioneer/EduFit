import type { TutorAvailabilitySlot } from "@/services/profile";
import type { UserRole } from "@/shared/ui/Sidebar";

export type { UserRole };

export type SessionMode = "ONLINE" | "OFFLINE";

export type SessionStatus =
  | "PROPOSED"
  | "SCHEDULED"
  | "COMPLETED"
  | "ABSENT"
  | "CANCELLED"
  | "REJECTED"
  | "WITHDRAWN"
  | "EXPIRED";

export type CancelReason =
  | "SCHEDULE_CONFLICT"
  | "PERSONAL_REASON"
  | "OTHER_PARTY_UNAVAILABLE"
  | "OTHER";

export interface CalendarItem {
  sessionId: string;
  classId: string;
  tutorId: string;
  studentId: string;
  startAt: string;
  endAt: string;
  mode: SessionMode | string;
  placeOrLink?: string | null;
  status: SessionStatus | string;
  isLateCancel: boolean;
}

export interface SessionDetail {
  sessionId: string;
  classId: string;
  tutorId: string;
  studentId: string;
  startAt: string;
  endAt: string;
  mode: SessionMode | string;
  placeOrLink?: string | null;
  repeatNote?: string | null;
  message?: string | null;
  status: SessionStatus | string;
  proposedBy: string;
  respondedBy?: string | null;
  respondedAt?: string | null;
  responseReason?: string | null;
  expiresAt: string;
  cancelledBy?: string | null;
  cancelledAt?: string | null;
  cancelReason?: CancelReason | string | null;
  cancelComment?: string | null;
  isLateCancel: boolean;
  outcomeRecordedBy?: string | null;
  outcomeRecordedAt?: string | null;
  version: number;
  createdAt: string;
}

export interface RescheduleProposal {
  proposalId: string;
  sessionId: string;
  proposedBy: string;
  newStartAt: string;
  newEndAt: string;
  reason?: string | null;
  status: string;
  expiresAt: string;
  respondedBy?: string | null;
  respondedAt?: string | null;
  responseReason?: string | null;
  createdAt: string;
}

export interface ProposeSessionPayload {
  classId: string;
  tutorId?: string;
  studentId?: string;
  startAt: string;
  endAt: string;
  mode: SessionMode;
  placeOrLink?: string;
  repeatNote?: string;
  message?: string;
}

export interface RespondProposalPayload {
  accept: boolean;
  reason?: string;
}

export interface RescheduleSessionPayload {
  newStartAt: string;
  newEndAt: string;
  reason?: string;
}

export interface RespondReschedulePayload {
  accept: boolean;
  responseReason?: string;
}

export interface CancelSessionPayload {
  reason: CancelReason;
  comment?: string;
}

export interface ConnectedClass {
  classId: string;
  title: string;
  tutorId: string;
  studentId: string;
  tutorName?: string;
  studentName?: string;
  subjectName?: string;
  educationLevelName?: string;
  status?: string;
}

/**
 * Thống nhất định dạng tên lớp học trên toàn hệ thống (Grid, Timeline, Modal):
 * [Tên Môn] - [Cấp học] • [Đối tác / Con] thay vì hiển thị mã UUID thô.
 */
export function formatConnectedClassName(
  cls?: ConnectedClass | null,
  role?: UserRole,
  fallbackClassId?: string
): string {
  if (!cls) {
    return fallbackClassId ? `Lớp #${fallbackClassId.slice(0, 8)}` : "Lớp học";
  }

  const subject = cls.subjectName?.trim();
  const level = cls.educationLevelName?.trim();

  if (subject) {
    const subjectAndLevel = level ? `${subject} - ${level}` : subject;

    if (role === "TUTOR") {
      return cls.studentName ? `${subjectAndLevel} • ${cls.studentName}` : subjectAndLevel;
    }
    if (role === "STUDENT") {
      return cls.tutorName ? `${subjectAndLevel} • ${cls.tutorName}` : subjectAndLevel;
    }
    if (role === "PARENT") {
      const partner = cls.tutorName ? ` • ${cls.tutorName}` : "";
      const child = cls.studentName ? ` (${cls.studentName})` : "";
      return `${subjectAndLevel}${partner}${child}`;
    }

    const partner = cls.studentName || cls.tutorName;
    return partner ? `${subjectAndLevel} • ${partner}` : subjectAndLevel;
  }

  if (cls.title) {
    return cls.title;
  }

  return fallbackClassId ? `Lớp #${fallbackClassId.slice(0, 8)}` : "Lớp học";
}

export type ScheduleViewMode = "grid" | "timeline";

export interface ScheduleCalendarViewProps {
  role: UserRole;
  availabilitySlots?: TutorAvailabilitySlot[];
  classes?: ConnectedClass[];
  initialStudentId?: string;
}

export interface WeeklySlotPayload {
  dayOfWeek: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
  startTime: string;
  endTime: string;
}

export interface ProposeRecurringPayload {
  classId: string;
  effectiveFrom: string;
  effectiveTo: string;
  newWeeklySlots: string[];
  mode?: SessionMode;
  placeOrLink?: string;
  message?: string;
}

export interface RespondRecurringPayload {
  proposalId: string;
  accept: boolean;
  reason?: string;
}

export interface RecurringProposalResponse {
  proposalId: string;
  classId: string;
  tutorId: string;
  studentId: string;
  proposedBy: string;
  effectiveFrom: string;
  effectiveTo: string;
  newWeeklySlots: string[];
  defaultMode?: SessionMode | string;
  placeOrLink?: string | null;
  message?: string | null;
  status: "PENDING" | "ACCEPTED" | "REJECTED" | "EXPIRED" | string;
  respondedBy?: string | null;
  respondedAt?: string | null;
  responseReason?: string | null;
  expiresAt: string;
  createdAt: string;
}

export interface RecurringScheduleResult {
  proposal: RecurringProposalResponse;
  cancelledSessionCount: number;
  generatedSessions: SessionDetail[];
}

