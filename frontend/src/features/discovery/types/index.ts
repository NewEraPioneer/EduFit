import type { CatalogRef, TutorOffering, TutorType } from "@/services/profile";

export type TeachingMode = "ONLINE" | "OFFLINE" | "BOTH";

/** Tiêu chí độ khớp, khớp với enum FitCriterion ở backend. */
export type FitCriterion = "LEVEL" | "GOAL" | "SCHEDULE" | "TEXTBOOK" | "BUDGET" | "LOCATION";

export interface FitCheck {
  criterion: FitCriterion;
  matched: boolean;
  /** Nhãn hiển thị (✔/⚠) do backend sinh, ví dụ "Dạy lớp 9". */
  label: string;
}

/** Độ khớp của gia sư với một nhu cầu học, chỉ có khi tìm/ghép kèm nhu cầu. */
export interface TutorFit {
  matchScore: number;
  offeringId: string;
  offeringName: string;
  pricePerSession: number;
  checks: FitCheck[];
}

export interface TutorDiscoveryCard {
  tutorId: string;
  displayName: string;
  headline: string | null;
  shortBio: string | null;
  teachingMode: TeachingMode;
  tutorType: TutorType | null;
  tutorTypeVerified: boolean;
  provinceName: string | null;
  districts: CatalogRef[];
  experienceYears: number | null;
  ratingAvg: number | null;
  reviewCount: number | null;
  verified: boolean;
  /** Học phí thấp nhất trong các gói dạy; null khi chưa có gói nào. */
  minPricePerSession: number | null;
  offerings: TutorOffering[];
  fit?: TutorFit;
}

export interface TutorAvailabilitySlot {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface TutorBusySlot {
  startAt: string;
  endAt: string;
}

export interface TutorDetail {
  tutorId: string;
  displayName: string;
  headline: string | null;
  bio: string | null;
  teachingMethod: string | null;
  teachingMode: TeachingMode;
  tutorType: TutorType | null;
  tutorTypeVerified: boolean;
  provinceName: string | null;
  districts: CatalogRef[];
  experienceYears: number | null;
  ratingAvg: number | null;
  reviewCount: number | null;
  verified: boolean;
  offerings: TutorOffering[];
  availabilitySlots: TutorAvailabilitySlot[];
  /** Khung giờ đã có lịch dạy trong 4 tuần tới. */
  busySlots?: TutorBusySlot[];
}

export type DiscoverySortBy = "relevance" | "rating" | "price" | "experience";

/** Bộ lọc tìm kiếm: mọi tiêu chí là lọc cứng; needId chỉ dùng để xếp hạng theo độ khớp. */
export interface TutorSearchParams {
  levelId?: number;
  subjectId?: number;
  trackId?: number;
  goalTagId?: number;
  textbookSeriesId?: number;
  tutorType?: TutorType;
  mode?: TeachingMode;
  provinceId?: number;
  districtId?: number;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  keyword?: string;
  dayOfWeek?: number;
  availableFrom?: string;
  availableTo?: string;
  needId?: string;
  page?: number;
  size?: number;
  sortBy?: DiscoverySortBy;
  sortDirection?: "asc" | "desc";
}

export interface SpringPage<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface TutorMatchResult {
  tutorId: string;
  matchScore: number;
  explanation: string;
  aiGenerated: boolean;
  tutorInfo: TutorDiscoveryCard;
}

export interface TutorMatchRequest {
  needId: string;
  topN?: number;
}
