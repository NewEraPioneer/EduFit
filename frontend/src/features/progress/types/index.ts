export type MilestoneStatus = "NOT_STARTED" | "IN_PROGRESS" | "ACHIEVED" | "PENDING" | "COMPLETED";

export interface MilestoneResponse {
  milestoneId: string;
  title: string;
  description?: string | null;
  targetDate?: string | null;
  weight: number; // 1 to 5
  status: MilestoneStatus;
  achievedDate?: string | null;
  isArchived: boolean;
  sortOrder: number;
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

export interface ProgressSeriesPoint {
  date: string;
  progressPercent: number;
}

export interface SessionNoteTimelineResponse {
  sessionId: string;
  classId: string;
  startAt: string;
  endAt: string;
  status: string; // "COMPLETED" | "ABSENT" | "SCHEDULED" etc.
  noteText?: string | null;
  recordedAt?: string | null;
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
  sessionStats?: ClassSessionStatsView | null;
  progressSeries?: ProgressSeriesPoint[];
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
}

export interface UpdatePlanPayload {
  version: number;
  summary?: string;
  milestones: UpdateMilestoneItemPayload[];
}

export interface UpdateMilestoneStatusPayload {
  version: number;
  status: "NOT_STARTED" | "IN_PROGRESS" | "ACHIEVED";
  achievedDate?: string;
}

export interface RecordSessionOutcomePayload {
  outcome: "COMPLETED" | "ABSENT";
  noteContent?: string;
}
