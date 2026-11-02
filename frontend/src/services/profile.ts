import { apiClient } from "@/lib/api";

export interface StudentProfileData {
  studentId: string;
  userId: string;
  educationLevelId?: number;
  educationLevelName?: string;
  /** Khu vực mặc định, dùng để điền sẵn khi tạo nhu cầu học có học trực tiếp. */
  provinceId?: number;
  provinceName?: string;
  districtId?: number;
  districtName?: string;
  profileComplete: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateStudentProfilePayload {
  educationLevelId?: number | null;
  provinceId?: number | null;
  districtId?: number | null;
}

export type TutorType = "UNIVERSITY_STUDENT" | "GRADUATE" | "IN_SERVICE_TEACHER" | "RETIRED_TEACHER";

export interface CatalogRef {
  id: number;
  name: string;
}

export interface OfferingGoal {
  goalTagId: number;
  code: string;
  name: string;
  premium: boolean;
  /** Mục tiêu premium đã được admin duyệt bằng chứng chuyên môn. */
  evidenceVerified: boolean;
}

/** Gói dạy: 1 môn (+ nhánh), các khối lớp, mục tiêu, bộ sách, tỉnh luyện đề và học phí riêng. */
export interface TutorOffering {
  offeringId: string;
  subjectId: number;
  subjectName: string;
  trackId: number | null;
  trackName: string | null;
  pricePerSession: number;
  levels: CatalogRef[];
  goals: OfferingGoal[];
  textbooks: CatalogRef[];
  examProvinces: CatalogRef[];
}

export interface SaveTutorOfferingPayload {
  subjectId: number;
  trackId: number | null;
  levelIds: number[];
  goalTagIds: number[];
  textbookSeriesIds: number[];
  examProvinceIds: number[];
  pricePerSession: number;
}

export interface TutorAvailabilitySlot {
  slotId?: string;
  dayOfWeek: number; // 1 = Monday .. 7 = Sunday
  startTime: string; // "HH:mm:ss" or "HH:mm"
  endTime: string;   // "HH:mm:ss" or "HH:mm"
}

export interface TutorProfileData {
  tutorId: string;
  userId: string;
  displayName: string;
  headline?: string;
  bio?: string;
  teachingMode: "ONLINE" | "OFFLINE" | "BOTH";
  tutorType: TutorType | null;
  tutorTypeVerified: boolean;
  provinceId: number | null;
  provinceName: string | null;
  districts: CatalogRef[];
  experienceYears?: number;
  teachingMethod?: string;
  status: string;
  verifiedAt?: string;
  ratingAvg?: number;
  reviewCount?: number;
  offerings: TutorOffering[];
  availabilitySlots: TutorAvailabilitySlot[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateTutorProfilePayload {
  displayName: string;
  headline?: string;
  bio?: string;
  teachingMode: "ONLINE" | "OFFLINE" | "BOTH";
  tutorType?: TutorType | null;
  provinceId?: number | null;
  districtIds?: number[];
  experienceYears?: number;
  teachingMethod?: string;
}

export interface GenerateTutorBioPayload {
  displayName?: string;
  subjects?: string[];
  educationLevels?: string[];
  experienceYears?: number;
  tone?: "friendly" | "professional" | "concise";
  focusArea?: string;
  customPrompt?: string;
  currentBio?: string;
}

export interface GenerateTutorBioResult {
  bio: string;
  isAiGenerated: boolean;
  remainingQuota: number;
}

export interface MyProfileComposite {
  userId: string;
  email: string;
  roles: string[];
  studentProfile?: StudentProfileData;
  tutorProfile?: TutorProfileData;
}

export const profileService = {
  /**
   * Lấy tổng hợp thông tin hồ sơ của tài khoản đang đăng nhập
   */
  async getMyProfile(): Promise<MyProfileComposite> {
    const res = await apiClient.get<MyProfileComposite>("/api/v1/profiles/me");
    return res.data as MyProfileComposite;
  },

  /**
   * Lấy hồ sơ học viên
   */
  async getMyStudentProfile(): Promise<StudentProfileData> {
    const res = await apiClient.get<StudentProfileData>("/api/v1/profiles/students/me");
    return res.data as StudentProfileData;
  },

  /**
   * Cập nhật hồ sơ học viên
   */
  async updateStudentProfile(payload: UpdateStudentProfilePayload): Promise<StudentProfileData> {
    const res = await apiClient.put<StudentProfileData>("/api/v1/profiles/students/me", payload);
    return res.data as StudentProfileData;
  },

  /**
   * Lấy hồ sơ gia sư
   */
  async getMyTutorProfile(): Promise<TutorProfileData> {
    const res = await apiClient.get<TutorProfileData>("/api/v1/profiles/tutors/me");
    return res.data as TutorProfileData;
  },

  /**
   * Cập nhật hồ sơ gia sư
   */
  async updateTutorProfile(payload: UpdateTutorProfilePayload): Promise<TutorProfileData> {
    const res = await apiClient.put<TutorProfileData>("/api/v1/profiles/tutors/me", payload);
    return res.data as TutorProfileData;
  },

  async createTutorOffering(payload: SaveTutorOfferingPayload): Promise<TutorProfileData> {
    const res = await apiClient.post<TutorProfileData>("/api/v1/profiles/tutors/me/offerings", payload);
    return res.data as TutorProfileData;
  },

  async updateTutorOffering(offeringId: string, payload: SaveTutorOfferingPayload): Promise<TutorProfileData> {
    const res = await apiClient.put<TutorProfileData>(
      `/api/v1/profiles/tutors/me/offerings/${encodeURIComponent(offeringId)}`,
      payload
    );
    return res.data as TutorProfileData;
  },

  async deleteTutorOffering(offeringId: string): Promise<TutorProfileData> {
    const res = await apiClient.delete<TutorProfileData>(
      `/api/v1/profiles/tutors/me/offerings/${encodeURIComponent(offeringId)}`
    );
    return res.data as TutorProfileData;
  },

  /**
   * Cập nhật danh sách khung giờ rảnh của gia sư
   */
  async setAvailabilitySlots(
    slots: Array<{ dayOfWeek: number; startTime: string; endTime: string }>
  ): Promise<TutorProfileData> {
    const res = await apiClient.put<TutorProfileData>("/api/v1/profiles/tutors/me/availability-slots", {
      slots,
    });
    return res.data as TutorProfileData;
  },

  /**
   * Sinh bản thảo tiểu sử gia sư (Bio) thông qua AI Assistant
   */
  async generateTutorBio(payload: GenerateTutorBioPayload): Promise<GenerateTutorBioResult> {
    const res = await apiClient.post<GenerateTutorBioResult>(
      "/api/v1/profiles/tutors/me/generate-bio",
      payload
    );
    return res.data as GenerateTutorBioResult;
  },
};
