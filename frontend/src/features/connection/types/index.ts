export interface ConnectionRequest {
  requestId: string;
  studentId: string;
  tutorId: string;
  needId: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED" | "EXPIRED" | string;
  createdAt: string;
  expiresAt: string;
}

export interface StudentLink {
  linkId: string;
  parentUserId: string;
  studentId: string;
  status: string;
  confirmedAt: string;
}

export interface LinkInvitation {
  invitationId: string;
  inviterUserId: string;
  invitedUserId: string;
  status: "PENDING" | "ACCEPTED" | "DECLINED" | "WITHDRAWN" | "EXPIRED";
  createdAt: string;
  expiresAt: string;
  reinviteAfter?: string | null;
  inviterName?: string | null;
  inviterRole?: string | null;
}

export interface SendConnectionRequestPayload {
  needId: string;
  tutorId: string;
  desiredSlots: string[];
  message?: string;
}

/** Nhu cầu học gửi kèm yêu cầu, rút gọn cho gia sư (không lộ thông tin liên hệ). */
export interface InboxNeedSummary {
  educationLevelName: string | null;
  subjectName: string | null;
  trackName: string | null;
  goalTagName: string | null;
  textbookSeriesName: string | null;
  examProvinceName: string | null;
  targetSchoolName: string | null;
  mode: "ONLINE" | "OFFLINE" | "BOTH" | null;
  provinceName: string | null;
  districtName: string | null;
  budgetMaxPerSession: number | null;
  availabilitySlots: Array<{ dayOfWeek: number; startTime: string; endTime: string }>;
}

export interface TutorInboxItem {
  requestId: string;
  desiredSlots: string[];
  message?: string | null;
  senderRole: "STUDENT" | "PARENT" | string;
  status: "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED" | "EXPIRED" | string;
  expiresAt: string;
  studentDisplayName?: string | null;
  need: InboxNeedSummary;
  /** Gói dạy của gia sư khớp với môn của nhu cầu; null khi gia sư đã gỡ gói này. */
  offeringName: string | null;
  /** Nhãn ✔/⚠ từng tiêu chí giữa gói dạy và nhu cầu. */
  fitChecks: Array<{ criterion: string; matched: boolean; label: string }>;
  hasSlotConflict: boolean;
  conflictingSlots: string[];
}

export interface TutoringClass {
  classId: string;
  studentId: string;
  tutorId: string;
  needId: string;
  status: "ACTIVE" | "ENDED" | "COMPLETED" | "CANCELLED" | string;
  studentDisplayName?: string | null;
  tutorDisplayName?: string | null;
  subjectName?: string | null;
  educationLevelName?: string | null;
  scheduleStatus?: "PROPOSED" | "SCHEDULED" | "COMPLETED" | string;
  sessionCount: number;
  completedCount?: number;
  scheduledCount?: number;
  totalCount?: number;
  startedAt: string;
  endedAt?: string | null;
  endReason?: string | null;
}

export interface RespondRequestPayload {
  accept: boolean;
  reason?: string;
}

export interface RespondRequestResult {
  classId?: string;
  studentId?: string;
  tutorId?: string;
  needId?: string;
}
