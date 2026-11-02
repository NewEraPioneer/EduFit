export type MilestoneStatus = "NOT_STARTED" | "IN_PROGRESS" | "ACHIEVED" | "PENDING" | "COMPLETED";

export interface MilestoneCriterion {
  criterionId: string;
  content: string;
  checked: boolean;
  checkedAt?: string | null;
}

export interface MilestoneResponse {
  milestoneId: string;
  title: string;
  description?: string | null;
  targetDate?: string | null;
  /** Hạn hiệu lực: targetDate nếu gia sư đặt, không thì ngày hệ thống nội suy giữa hai mốc đã biết. */
  expectedDate?: string | null;
  dateInterpolated?: boolean;
  weight: number; // 1 to 5
  status: MilestoneStatus;
  achievedDate?: string | null;
  isArchived: boolean;
  sortOrder: number;
  criteria: MilestoneCriterion[];
  /** Số buổi gợi ý 2–6 (tùy chọn), dùng cho dự báo "còn X buổi". */
  suggestedSessions?: number | null;
  /** Chủ đề chuẩn của môn × khối (tùy chọn), trục của radar năng lực. */
  topicId?: string | null;
}

// Backwards-compatible alias for existing views
export type MilestoneItem = MilestoneResponse;

export interface PlanResponse {
  planId: string;
  classId: string;
  summary?: string | null;
  version: number;
  progressPercentage?: number | null;
  milestones: MilestoneResponse[];
  createdAt: string;
  updatedAt: string;
}

// Backwards-compatible alias for existing views
export interface PlanItem extends PlanResponse {
  title?: string;
  totalMilestones?: number;
  completedMilestones?: number;
}

export interface ClassSessionStatsView {
  totalSessions?: number;
  completedSessions?: number;
  completedCount?: number;
  absentCount?: number;
  absentSessions?: number;
  upcomingCount?: number;
  upcomingSessions?: number;
  cancelledCount?: number;
  cancelledSessions?: number;
  nextSessionStart?: string | null;
  lastSessionStart?: string | null;
}

// Backwards-compatible alias
export type ClassSessionStats = ClassSessionStatsView;

export interface SessionNoteTimelineResponse {
  sessionId: string;
  classId: string;
  startAt: string;
  endAt: string;
  status: string; // "COMPLETED" | "ABSENT" | "SCHEDULED" etc.
  noteText?: string | null;
  recordedAt?: string | null;
  feedback?: SessionFeedbackView | null;
  /** Cảm nhận 1 chạm của học sinh (1–3); backend không trả cho gia sư. */
  selfRating?: SelfRatingValue | null;
}

/** Cột mốc của mẫu lộ trình (không có ngày; gia sư đặt hạn khi áp dụng). */
export interface TemplateMilestone {
  title: string;
  description?: string | null;
  weight: number;
  /** Số buổi gợi ý 2–6. */
  suggestedSessions: number;
  criteria: string[];
}

/** Mẫu lộ trình theo môn × khối × mục tiêu. */
export interface PlanTemplate {
  templateId: string;
  /** Mẫu hệ thống do admin quản lý. */
  system: boolean;
  /** Mẫu riêng của gia sư đang xem. */
  mine: boolean;
  subjectId: number;
  educationLevelId: number;
  goalTagId?: number | null;
  title: string;
  description?: string | null;
  milestones: TemplateMilestone[];
  version: number;
  updatedAt: string;
}

/** Tạo/sửa mẫu: danh mục lấy từ classId (gia sư) hoặc nhập trực tiếp (admin). */
export interface SavePlanTemplatePayload {
  classId?: string;
  subjectId?: number;
  educationLevelId?: number;
  goalTagId?: number | null;
  title: string;
  description?: string;
  milestones: TemplateMilestone[];
  version?: number;
}

/** Cột mốc hiệu lực của một buổi học trên lịch: thực tế (đã phản hồi) thắng dự kiến. */
export interface SessionMilestones {
  sessionId: string;
  linkType: "PLANNED" | "ACTUAL";
  milestones: { milestoneId: string; title: string }[];
}

/** Ngăn "Việc cần làm" trên dashboard gia sư. */
export interface TutorTodos {
  sessionsMissingFeedback: {
    sessionId: string;
    classId: string;
    studentName?: string | null;
    startAt: string;
    endAt: string;
    /** Quá 48 giờ: phản hồi bây giờ sẽ được đánh dấu "bổ sung muộn". */
    late: boolean;
  }[];
  classesWithoutPlan: TutorTodoClass[];
  overdueMilestones: {
    classId: string;
    studentName?: string | null;
    milestoneId: string;
    milestoneTitle: string;
    targetDate: string;
    daysLate: number;
  }[];
  plansMissingFinalDate: TutorTodoClass[];
  /** Báo cáo kỳ đang là bản nháp: gia sư viết câu tổng kết và gửi trước autoSendAt. */
  pendingReports: { studentName?: string | null; classTitle?: string | null; report: PeriodReport }[];
}

export interface TutorTodoClass {
  classId: string;
  studentName?: string | null;
  classTitle?: string | null;
}

/** Cảm nhận 1 chạm của học sinh sau buổi: 1 = còn khó, 2 = tạm ổn, 3 = hiểu bài. */
export type SelfRatingValue = 1 | 2 | 3;

/** Mức độ tự lập 1–4 (thang duy nhất đo tiến bộ theo cột mốc). */
export type IndependenceLevel = 1 | 2 | 3 | 4;

export type HomeworkStatus = "ASSIGNED" | "NONE" | "PREVIOUSLY_ASSIGNED";

export type AttitudeChip =
  | "FOCUSED"
  | "ASKS_QUESTIONS"
  | "HARD_WORKING"
  | "NEEDS_MORE_FOCUS"
  | "NEEDS_MORE_PRACTICE"
  | "NEEDS_PREPARATION";

export interface SessionFeedbackAssessment {
  milestoneId: string;
  milestoneTitle?: string | null;
  level: IndependenceLevel;
  correctCount?: number | null;
  totalCount?: number | null;
  evidenceNote?: string | null;
}

export interface SessionFeedbackView {
  homeworkStatus: HomeworkStatus;
  attitudeChips: AttitudeChip[];
  assessments: SessionFeedbackAssessment[];
  /** Lời nhắn riêng cho phụ huynh; backend luôn trả null khi người xem là học sinh. */
  parentMessage?: string | null;
  /** Phản hồi được bổ sung sau 48 giờ kể từ khi buổi học kết thúc. */
  late?: boolean;
  createdAt: string;
}

export type OverviewStage = "NO_PLAN" | "PLAN_NO_SESSIONS" | "BASELINE_PENDING" | "READY";

export type OnTrackStatus =
  | "AHEAD"
  | "ON_TRACK"
  | "NEEDS_ATTENTION"
  | "BEHIND"
  | "NOT_STARTED"
  | "NOT_ENOUGH_DATA"
  | "NO_PLAN";

export type MissedSessionKind =
  | "ABSENT"
  | "LATE_CANCEL_BY_FAMILY"
  | "EARLY_CANCEL_BY_FAMILY"
  | "CANCELLED_BY_TUTOR"
  | "CANCELLED_OTHER";

export interface ProgressOverview {
  stage: OverviewStage;
  onTrack: { status: OnTrackStatus; explanation: string };
  goalProgress: {
    goalTitle?: string | null;
    progressPercentage?: number | null;
    progressDelta?: number | null;
    highlightKind?: string | null;
    highlight?: string | null;
    reinforcement?: string | null;
    latestComment?: string | null;
    planAdjusted: boolean;
    windowStart: string;
    windowEnd: string;
  };
  attendance: {
    attendedCount: number;
    countedCount: number;
    attendanceRate?: number | null;
    absentCount: number;
    familyLateCancelCount: number;
    familyEarlyCancelCount: number;
    tutorCancelCount: number;
    nextSessionStart?: string | null;
    /** Cột mốc dự kiến của buổi tới nếu gia sư đã gắn (nextSessionPlanned), ngược lại là cột mốc đang học. */
    focusMilestoneTitle?: string | null;
    nextSessionPlanned?: boolean;
    homeworkAssigned: boolean;
    missedSessions: { sessionId: string; startAt: string; kind: MissedSessionKind }[];
  };
  milestoneLevels: {
    milestoneId: string;
    startLevel?: IndependenceLevel | null;
    currentLevel?: IndependenceLevel | null;
    sessionCount: number;
  }[];
  /** Cảm nhận 1 chạm của học sinh trong 30 ngày, cũ trước mới sau; rỗng với gia sư. */
  selfRatings?: { sessionId: string; startAt: string; rating: SelfRatingValue }[];
  forecast?: ProgressForecast | null;
  radar?: ProgressRadar | null;
}

/**
 * Dự báo "còn X buổi cho Y cột mốc". basis = PACE (nhịp thực tế) hoặc SUGGESTED (số buổi gợi ý);
 * estimatedFinishDate chỉ có khi lớp học đều. Không dự báo được thì chỉ có unavailableReason.
 */
export interface ProgressForecast {
  remainingSessions?: number | null;
  remainingMilestones?: number | null;
  basis?: "PACE" | "SUGGESTED" | null;
  estimatedFinishDate?: string | null;
  unavailableReason?: string | null;
}

/** Radar năng lực theo chủ đề chuẩn; ít hơn 3 trục thì axes rỗng và có unavailableReason. */
export interface ProgressRadar {
  axes: { topicId: string; topicName: string; level: IndependenceLevel }[];
  unavailableReason?: string | null;
}

export type PeriodReportStatus = "DRAFT" | "SENT";

/** Báo cáo kỳ 30 ngày: ảnh chụp 3 thẻ + câu tổng kết tùy chọn của gia sư. */
export interface PeriodReport {
  reportId: string;
  classId: string;
  periodIndex: number;
  periodStart: string;
  periodEnd: string;
  onTrackStatus: OnTrackStatus;
  onTrackExplanation: string;
  progressPercentage?: number | null;
  progressDelta?: number | null;
  attendedCount: number;
  countedCount: number;
  highlight?: string | null;
  nextFocusTitle?: string | null;
  achievedMilestones: string[];
  tutorSummary?: string | null;
  status: PeriodReportStatus;
  autoSendAt: string;
  sentAt?: string | null;
  version: number;
}

/** Chủ đề chuẩn theo môn × khối do admin quản lý. */
export interface Topic {
  topicId: string;
  subjectId: number;
  educationLevelId: number;
  name: string;
  sortOrder: number;
  archived: boolean;
  version: number;
}

/** subjectId/educationLevelId chỉ dùng khi tạo; version chỉ dùng khi sửa. */
export interface SaveTopicPayload {
  subjectId?: number;
  educationLevelId?: number;
  name: string;
  sortOrder?: number;
  archived?: boolean;
  version?: number;
}

/** Bản nháp lộ trình bằng AI; milestones rỗng thì unavailableReason gợi ý dùng mẫu. */
export interface PlanDraft {
  milestones: {
    title: string;
    description?: string | null;
    weight: number;
    suggestedSessions: number;
    criteria: string[];
    topicId?: string | null;
  }[];
  unavailableReason?: string | null;
  remainingQuota: number;
}

// Backwards-compatible alias
export interface SessionNoteItem extends SessionNoteTimelineResponse {
  sessionNoteId?: string;
  sessionDate?: string;
  summary?: string;
  homeworkAssigned?: string;
  studentAttitude?: string;
  nextSteps?: string;
  createdAt?: string;
}

export interface ClassSelectorItem {
  classId: string;
  title: string;
  status: string;
  progressPercentage?: number | null;
}

export interface StudentDashboardResponse {
  studentId: string;
  studentName: string;
  classes: ClassSelectorItem[];
  selectedClassId?: string | null;
  tutorId?: string | null;
  tutorName?: string | null;
  needId?: string | null;
  goalTitle?: string | null;
  progressPercentage?: number | null;
  plan?: PlanResponse | null;
  overview?: ProgressOverview | null;
  recentNotes?: SessionNoteTimelineResponse[];
  hasMoreNotes?: boolean;
}

// Backwards-compatible alias
export type StudentDashboardData = StudentDashboardResponse;

export interface ParentStudentSummary {
  studentId: string;
  studentName: string;
  classes: ClassSelectorItem[];
  activeClassDashboard?: StudentDashboardResponse | null;
}

export interface ParentDashboardResponse {
  students: ParentStudentSummary[];
}

// Backwards-compatible alias
export type ParentDashboardData = ParentDashboardResponse;

export interface TeachingHistoryItem {
  classId: string;
  studentId: string;
  studentName: string;
  needId?: string | null;
  goalTitle?: string | null;
  status: string;
  progressPercentage?: number | null;
  startedAt?: string | null;
  endedAt?: string | null;
  sessionStats?: ClassSessionStatsView | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// Payload DTOs
export interface CreateMilestoneItemPayload {
  title: string;
  description?: string;
  targetDate?: string;
  weight?: number;
  criteria: string[];
  suggestedSessions?: number;
  topicId?: string;
}

export interface CreatePlanPayload {
  classId: string;
  summary?: string;
  milestones: CreateMilestoneItemPayload[];
}

export interface UpdateMilestoneItemPayload {
  milestoneId?: string;
  title: string;
  description?: string;
  targetDate?: string;
  weight?: number;
  criteria: { criterionId?: string; content: string }[];
  suggestedSessions?: number;
  topicId?: string;
}

export interface UpdatePlanPayload {
  version: number;
  summary?: string;
  milestones: UpdateMilestoneItemPayload[];
}

export interface UpdateMilestoneStatusPayload {
  version: number;
  status: "IN_PROGRESS" | "ACHIEVED";
  achievedDate?: string;
  /** Bắt buộc khi mở lại cột mốc đã đạt (ACHIEVED → IN_PROGRESS). */
  reason?: string;
}

export interface RecordSessionOutcomePayload {
  outcome: "COMPLETED" | "ABSENT";
  noteContent?: string;
}

export interface TaughtMilestonePayload {
  milestoneId: string;
  level: IndependenceLevel;
  correctCount?: number;
  totalCount?: number;
  /** Bắt buộc (hoặc đúng x/y) khi tăng từ 2 mức trở lên so với lần chấm trước. */
  evidenceNote?: string;
  /** Trạng thái tick đầy đủ của tiêu chí sau buổi học. */
  checkedCriterionIds: string[];
  markAchieved: boolean;
}

export interface SubmitSessionFeedbackPayload {
  homeworkStatus: HomeworkStatus;
  attitudeChips: AttitudeChip[];
  /** Lời nhắn riêng cho phụ huynh (tùy chọn), học sinh không thấy. */
  parentMessage?: string;
  milestones: TaughtMilestonePayload[];
}
