import type { LearningNeed } from "@/services/learningNeeds";
import type { LearningCatalog } from "@/services/catalog";
import type { TutorOffering } from "@/services/profile";
import type { TutorDetail, TutorDiscoveryCard, TutorFit, TutorMatchResult } from "@/features/discovery/types";
import type { TutorInboxItem } from "@/features/connection/types";

/** Danh mục tối thiểu: Lớp 9, Toán (không nhánh), Tiếng Anh (2 nhánh), 3 mục tiêu, 1 bộ sách, HN có khu vực. */
export function makeCatalog(): LearningCatalog {
  return {
    educationLevels: [
      { levelId: 3, name: "Lớp 8", sortOrder: 3 },
      { levelId: 4, name: "Lớp 9", sortOrder: 4 },
    ],
    subjects: [
      { subjectId: 1, name: "Toán" },
      { subjectId: 2, name: "Tiếng Anh" },
    ],
    tracks: [
      { trackId: 10, subjectId: 2, code: "GLOBAL", name: "Chương trình hiện hành", levelIds: [3, 4] },
      { trackId: 11, subjectId: 2, code: "PILOT", name: "Thí điểm", levelIds: [3, 4] },
    ],
    goalTags: [
      { goalTagId: 1, code: "GRADE_BOOST", name: "Nâng điểm trên lớp", requiresTextbook: true, requiresExamProvince: false, premium: false },
      { goalTagId: 2, code: "GRADE10_EXAM", name: "Luyện thi vào 10", requiresTextbook: false, requiresExamProvince: true, premium: false },
      { goalTagId: 3, code: "GIFTED", name: "Bồi dưỡng HSG", requiresTextbook: false, requiresExamProvince: false, premium: true },
    ],
    goalTagRules: [
      { goalTagId: 1, subjectId: 1, trackId: null, levelId: 3 },
      { goalTagId: 1, subjectId: 1, trackId: null, levelId: 4 },
      { goalTagId: 2, subjectId: 1, trackId: null, levelId: 4 },
      { goalTagId: 3, subjectId: 1, trackId: null, levelId: 4 },
      { goalTagId: 1, subjectId: 2, trackId: 10, levelId: 4 },
    ],
    textbooks: [{ seriesId: 5, code: "KNTT", name: "Kết nối tri thức", subjectIds: [1, 2] }],
    provinces: [
      { provinceId: 1, name: "Hà Nội" },
      { provinceId: 2, name: "Huế" },
    ],
    districts: [{ districtId: 100, provinceId: 1, name: "Cầu Giấy" }],
    specializedSchools: [],
  };
}

export function makeNeed(overrides: Partial<LearningNeed> = {}): LearningNeed {
  return {
    needId: "need-1",
    studentId: "student-1",
    educationLevelId: 4,
    educationLevelName: "Lớp 9",
    subjectId: 1,
    subjectName: "Toán",
    trackId: null,
    trackName: null,
    goalTagId: 2,
    goalTagCode: "GRADE10_EXAM",
    goalTagName: "Luyện thi vào 10",
    goalTagPremium: false,
    textbookSeriesId: null,
    textbookSeriesName: null,
    examProvinceId: 1,
    examProvinceName: "Hà Nội",
    targetSchoolId: null,
    targetSchoolName: null,
    mode: "ONLINE",
    provinceId: null,
    provinceName: null,
    districtId: null,
    districtName: null,
    budgetMaxPerSession: 300000,
    status: "ACTIVE",
    availabilitySlots: [{ dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" }],
    missingFields: [],
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
    ...overrides,
  };
}

export function makeOffering(overrides: Partial<TutorOffering> = {}): TutorOffering {
  return {
    offeringId: "offering-1",
    subjectId: 1,
    subjectName: "Toán",
    trackId: null,
    trackName: null,
    pricePerSession: 250000,
    levels: [{ id: 3, name: "Lớp 8" }, { id: 4, name: "Lớp 9" }],
    goals: [{ goalTagId: 2, code: "GRADE10_EXAM", name: "Luyện thi vào 10", premium: false, evidenceVerified: false }],
    textbooks: [],
    examProvinces: [{ id: 1, name: "Hà Nội" }],
    ...overrides,
  };
}

export function makeFit(overrides: Partial<TutorFit> = {}): TutorFit {
  return {
    matchScore: 86,
    offeringId: "offering-1",
    offeringName: "Toán",
    pricePerSession: 250000,
    checks: [
      { criterion: "LEVEL", matched: true, label: "Dạy Lớp 9" },
      { criterion: "BUDGET", matched: false, label: "Học phí vượt ngân sách" },
    ],
    ...overrides,
  };
}

export function makeTutorCard(overrides: Partial<TutorDiscoveryCard> = {}): TutorDiscoveryCard {
  return {
    tutorId: "tutor-1",
    displayName: "Cô Lan",
    headline: "Giáo viên Toán THCS",
    shortBio: null,
    teachingMode: "ONLINE",
    tutorType: "IN_SERVICE_TEACHER",
    tutorTypeVerified: true,
    provinceName: null,
    districts: [],
    experienceYears: 5,
    ratingAvg: 4.8,
    reviewCount: 12,
    verified: true,
    minPricePerSession: 250000,
    offerings: [makeOffering()],
    ...overrides,
  };
}

export function makeTutorDetail(overrides: Partial<TutorDetail> = {}): TutorDetail {
  return {
    tutorId: "tutor-1",
    displayName: "Cô Lan",
    headline: "Giáo viên Toán THCS",
    bio: "Dạy Toán 10 năm.",
    teachingMethod: null,
    teachingMode: "ONLINE",
    tutorType: "IN_SERVICE_TEACHER",
    tutorTypeVerified: true,
    provinceName: null,
    districts: [],
    experienceYears: 5,
    ratingAvg: 4.8,
    reviewCount: 12,
    verified: true,
    offerings: [makeOffering()],
    availabilitySlots: [{ dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" }],
    ...overrides,
  };
}

export function makeMatch(overrides: Partial<TutorMatchResult> = {}): TutorMatchResult {
  return {
    tutorId: "tutor-1",
    matchScore: 86,
    explanation: "Cô dạy đúng lớp và mục tiêu.",
    aiGenerated: false,
    tutorInfo: makeTutorCard({ fit: makeFit() }),
    ...overrides,
  };
}

export function makeInboxItem(overrides: Partial<TutorInboxItem> = {}): TutorInboxItem {
  return {
    requestId: "request-1",
    desiredSlots: ["Thứ 3 19:00-21:00"],
    message: "Mong được học thử",
    senderRole: "STUDENT",
    status: "PENDING",
    expiresAt: "2030-01-01T00:00:00Z",
    studentDisplayName: "Minh An",
    need: {
      educationLevelName: "Lớp 9",
      subjectName: "Toán",
      trackName: null,
      goalTagName: "Luyện thi vào 10",
      textbookSeriesName: null,
      examProvinceName: "Hà Nội",
      targetSchoolName: null,
      mode: "ONLINE",
      provinceName: null,
      districtName: null,
      budgetMaxPerSession: 300000,
      availabilitySlots: [{ dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" }],
    },
    offeringName: "Toán",
    fitChecks: [{ criterion: "LEVEL", matched: true, label: "Dạy Lớp 9" }],
    hasSlotConflict: false,
    conflictingSlots: [],
    ...overrides,
  };
}
