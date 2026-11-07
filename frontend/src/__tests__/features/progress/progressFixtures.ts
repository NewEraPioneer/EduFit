import type { LearningCatalog } from "@/services/catalog";
import type {
  MilestoneResponse,
  PeriodReport,
  PlanResponse,
  Topic,
  ProgressOverview,
  StudentDashboardResponse,
} from "@/features/progress/types";

/** Dữ liệu kiểm thử tập trung cho các component tiến độ. */
export const FIXTURE_IDS = {
  session: "11111111-1111-1111-1111-111111111111",
  milestoneA: "22222222-2222-2222-2222-222222222222",
  milestoneB: "33333333-3333-3333-3333-333333333333",
  criterionA1: "44444444-4444-4444-4444-444444444441",
  criterionA2: "44444444-4444-4444-4444-444444444442",
  plan: "55555555-5555-5555-5555-555555555555",
  class: "66666666-6666-6666-6666-666666666666",
  student: "77777777-7777-7777-7777-777777777777",
  template: "88888888-8888-8888-8888-888888888888",
  report: "99999999-9999-9999-9999-999999999999",
  topicAlgebra: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
  topicGeometry: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2",
  topicStatistics: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3",
} as const;

export const FIXTURE_TEXT = {
  milestoneA: "Hệ phương trình bậc nhất",
  milestoneB: "Hình học không gian",
  criterionA1: "Giải bằng phương pháp thế",
  criterionA2: "Giải bài toán lời văn",
  goalTitle: "Luyện thi vào 10 · Toán · Lớp 9",
  studentName: "Trần Minh",
  evidence: "Tự giải đúng 3 bài hệ đối xứng",
  milestoneFinal: "Đề thi thử vào 10",
  criterionFinal: "Đạt 8 điểm đề thi thử",
  finalTargetDate: "2026-12-20",
  templateTitle: "Toán 9 ôn thi vào 10",
  parentMessage: "Gia đình nhắc con ngủ sớm hơn trước hôm có buổi học",
  topicAlgebra: "Đại số",
  topicGeometry: "Hình học",
  topicStatistics: "Thống kê",
  reportSummary: "Con tiến bộ rõ ở hệ phương trình, kỳ tới tập trung hình học",
  aiUnavailable: "AI hiện không khả dụng. Hãy chọn mẫu lộ trình phù hợp hoặc tự lập lộ trình.",
  forecastUnavailable: "Chưa đủ buổi học để ước lượng nhịp học",
} as const;

export const FIXTURE_TOPICS = [
  { topicId: FIXTURE_IDS.topicAlgebra, name: FIXTURE_TEXT.topicAlgebra },
  { topicId: FIXTURE_IDS.topicGeometry, name: FIXTURE_TEXT.topicGeometry },
  { topicId: FIXTURE_IDS.topicStatistics, name: FIXTURE_TEXT.topicStatistics },
] as const;

export function buildTopic(index: number, overrides: Partial<Topic> = {}): Topic {
  const t = FIXTURE_TOPICS[index];
  return {
    topicId: t.topicId,
    subjectId: CATALOG.subjectId,
    educationLevelId: CATALOG.levelId,
    name: t.name,
    sortOrder: index,
    archived: false,
    version: 0,
    ...overrides,
  };
}

export function buildPeriodReport(overrides: Partial<PeriodReport> = {}): PeriodReport {
  return {
    reportId: FIXTURE_IDS.report,
    classId: FIXTURE_IDS.class,
    periodIndex: 1,
    periodStart: "2026-09-01",
    periodEnd: "2026-09-30",
    onTrackStatus: "ON_TRACK",
    onTrackExplanation: "Không có cột mốc nào quá hạn",
    progressPercentage: 40,
    progressDelta: 20,
    attendedCount: 7,
    countedCount: 8,
    highlight: null,
    nextFocusTitle: FIXTURE_TEXT.milestoneB,
    achievedMilestones: [FIXTURE_TEXT.milestoneA],
    tutorSummary: null,
    status: "DRAFT",
    autoSendAt: "2026-10-04T03:00:00Z",
    sentAt: null,
    version: 0,
    ...overrides,
  };
}

export function buildMilestone(overrides: Partial<MilestoneResponse> = {}): MilestoneResponse {
  return {
    milestoneId: FIXTURE_IDS.milestoneA,
    title: FIXTURE_TEXT.milestoneA,
    description: null,
    targetDate: "2026-11-20",
    weight: 2,
    status: "IN_PROGRESS",
    achievedDate: null,
    isArchived: false,
    sortOrder: 0,
    criteria: [
      { criterionId: FIXTURE_IDS.criterionA1, content: FIXTURE_TEXT.criterionA1, checked: false, checkedAt: null },
      { criterionId: FIXTURE_IDS.criterionA2, content: FIXTURE_TEXT.criterionA2, checked: false, checkedAt: null },
    ],
    ...overrides,
  };
}

export function buildPlan(overrides: Partial<PlanResponse> = {}): PlanResponse {
  return {
    planId: FIXTURE_IDS.plan,
    classId: FIXTURE_IDS.class,
    summary: null,
    version: 1,
    progressPercentage: 25,
    milestones: [
      buildMilestone(),
      buildMilestone({
        milestoneId: FIXTURE_IDS.milestoneB,
        title: FIXTURE_TEXT.milestoneB,
        status: "NOT_STARTED",
        sortOrder: 1,
        criteria: [],
      }),
    ],
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
    ...overrides,
  };
}

export function buildOverview(overrides: Partial<ProgressOverview> = {}): ProgressOverview {
  return {
    stage: "READY",
    onTrack: { status: "NEEDS_ATTENTION", explanation: `Cột mốc “${FIXTURE_TEXT.milestoneA}” trễ 2 ngày so với kế hoạch` },
    goalProgress: {
      goalTitle: FIXTURE_TEXT.goalTitle,
      progressPercentage: 65,
      progressDelta: 20,
      highlightKind: "LEVEL_UP",
      highlight: `${FIXTURE_TEXT.milestoneA}: từ “làm được khi có gợi ý” → “tự làm đúng”`,
      reinforcement: null,
      latestComment: null,
      planAdjusted: false,
      windowStart: "2026-10-03",
      windowEnd: "2026-11-02",
    },
    attendance: {
      attendedCount: 7,
      countedCount: 8,
      totalCount: 12,
      attendanceRate: 88,
      absentCount: 1,
      familyLateCancelCount: 0,
      familyEarlyCancelCount: 0,
      tutorCancelCount: 1,
      nextSessionStart: "2026-11-05T11:00:00Z",
      focusMilestoneTitle: FIXTURE_TEXT.milestoneA,
      homeworkAssigned: true,
      missedSessions: [{ sessionId: FIXTURE_IDS.session, startAt: "2026-10-20T11:00:00Z", kind: "ABSENT" }],
    },
    milestoneLevels: [{ milestoneId: FIXTURE_IDS.milestoneA, startLevel: 2, currentLevel: 3, sessionCount: 3 }],
    ...overrides,
  };
}

export function buildStudentDashboard(overrides: Partial<StudentDashboardResponse> = {}): StudentDashboardResponse {
  return {
    studentId: FIXTURE_IDS.student,
    studentName: FIXTURE_TEXT.studentName,
    classes: [{ classId: FIXTURE_IDS.class, title: FIXTURE_TEXT.goalTitle, status: "ACTIVE", progressPercentage: 65 }],
    selectedClassId: FIXTURE_IDS.class,
    goalTitle: FIXTURE_TEXT.goalTitle,
    progressPercentage: 65,
    plan: buildPlan(),
    overview: buildOverview(),
    recentNotes: [
      {
        sessionId: FIXTURE_IDS.session,
        classId: FIXTURE_IDS.class,
        startAt: "2026-10-30T11:00:00Z",
        endAt: "2026-10-30T12:30:00Z",
        status: "COMPLETED",
        noteText: "Con làm nhanh hơn, cần cẩn thận dấu.",
        recordedAt: "2026-10-30T12:35:00Z",
        feedback: {
          homeworkStatus: "ASSIGNED",
          attitudeChips: ["FOCUSED"],
          assessments: [
            { milestoneId: FIXTURE_IDS.milestoneA, milestoneTitle: FIXTURE_TEXT.milestoneA, level: 3, correctCount: 7, totalCount: 10 },
          ],
          createdAt: "2026-10-30T12:35:00Z",
        },
      },
    ],
    hasMoreNotes: false,
    ...overrides,
  };
}

/** Danh mục môn × khối × mục tiêu dùng cho các màn admin. */
export const CATALOG = {
  subjectId: 1,
  subjectName: "Toán",
  levelId: 9,
  levelName: "Lớp 9",
  goalTagId: 3,
  goalName: "Luyện thi vào 10",
} as const;

export const catalog = {
  educationLevels: [{ levelId: CATALOG.levelId, name: CATALOG.levelName, sortOrder: 9 }],
  subjects: [{ subjectId: CATALOG.subjectId, name: CATALOG.subjectName }],
  tracks: [],
  goalTags: [
    {
      goalTagId: CATALOG.goalTagId,
      code: "EXAM_10",
      name: CATALOG.goalName,
      requiresTextbook: false,
      requiresExamProvince: false,
      premium: false,
    },
  ],
  goalTagRules: [{ goalTagId: CATALOG.goalTagId, subjectId: CATALOG.subjectId, trackId: null, levelId: CATALOG.levelId }],
  textbooks: [],
  provinces: [],
  districts: [],
  specializedSchools: [],
} satisfies LearningCatalog;
