import { apiClient } from "@/lib/api";

export type TeachingMode = "ONLINE" | "OFFLINE" | "BOTH";
export type NeedStatus = "INCOMPLETE" | "ACTIVE" | "ARCHIVED";

export interface WeeklySlot {
  dayOfWeek: number; // 1 = Thứ 2 .. 7 = Chủ nhật
  startTime: string; // "HH:mm" hoặc "HH:mm:ss"
  endTime: string;
}

/** Nhu cầu học kèm tên hiển thị đã tra từ danh mục (do backend trả về). */
export interface LearningNeed {
  needId: string;
  studentId: string;
  educationLevelId: number | null;
  educationLevelName: string | null;
  subjectId: number;
  subjectName: string | null;
  trackId: number | null;
  trackName: string | null;
  goalTagId: number | null;
  goalTagCode: string | null;
  goalTagName: string | null;
  goalTagPremium: boolean;
  textbookSeriesId: number | null;
  textbookSeriesName: string | null;
  examProvinceId: number | null;
  examProvinceName: string | null;
  targetSchoolId: number | null;
  targetSchoolName: string | null;
  mode: TeachingMode | null;
  provinceId: number | null;
  provinceName: string | null;
  districtId: number | null;
  districtName: string | null;
  budgetMaxPerSession: number | null;
  status: NeedStatus;
  availabilitySlots: WeeklySlot[];
  /** Tên trường còn thiếu để nhu cầu được ghép gia sư (rỗng khi ACTIVE). */
  missingFields: string[];
  createdAt: string;
  updatedAt: string;
}

/** Tạo hoặc thay thế toàn bộ nhu cầu (kèm khung giờ) trong một lời gọi. */
export interface SaveLearningNeedPayload {
  studentId?: string;
  educationLevelId: number;
  subjectId: number;
  trackId?: number | null;
  goalTagId: number;
  textbookSeriesId?: number | null;
  examProvinceId?: number | null;
  targetSchoolId?: number | null;
  mode?: TeachingMode | null;
  provinceId?: number | null;
  districtId?: number | null;
  budgetMaxPerSession?: number | null;
  availabilitySlots?: WeeklySlot[];
}

const ROOT = "/api/v1/learning-needs";

export const learningNeedService = {
  /** Học sinh: nhu cầu của mình. Phụ huynh: truyền studentId của con đã liên kết. */
  async list(studentId?: string): Promise<LearningNeed[]> {
    const query = studentId ? `?studentId=${encodeURIComponent(studentId)}` : "";
    const res = await apiClient.get<LearningNeed[]>(`${ROOT}${query}`);
    return res.data ?? [];
  },

  async create(payload: SaveLearningNeedPayload): Promise<LearningNeed> {
    const res = await apiClient.post<LearningNeed>(ROOT, payload);
    return res.data as LearningNeed;
  },

  async update(needId: string, payload: SaveLearningNeedPayload): Promise<LearningNeed> {
    const res = await apiClient.put<LearningNeed>(`${ROOT}/${encodeURIComponent(needId)}`, payload);
    return res.data as LearningNeed;
  },

  async archive(needId: string): Promise<LearningNeed> {
    const res = await apiClient.post<LearningNeed>(`${ROOT}/${encodeURIComponent(needId)}/archive`);
    return res.data as LearningNeed;
  },
};

/** Payload đầy đủ từ một nhu cầu đã có, để chỉnh sửa một phần mà vẫn thay thế toàn bộ. */
export function needToPayload(need: LearningNeed): SaveLearningNeedPayload {
  return {
    educationLevelId: need.educationLevelId ?? 0,
    subjectId: need.subjectId,
    trackId: need.trackId,
    goalTagId: need.goalTagId ?? 0,
    textbookSeriesId: need.textbookSeriesId,
    examProvinceId: need.examProvinceId,
    targetSchoolId: need.targetSchoolId,
    mode: need.mode,
    provinceId: need.provinceId,
    districtId: need.districtId,
    budgetMaxPerSession: need.budgetMaxPerSession,
    availabilitySlots: need.availabilitySlots.map((slot) => ({
      dayOfWeek: slot.dayOfWeek,
      startTime: slot.startTime.slice(0, 5),
      endTime: slot.endTime.slice(0, 5),
    })),
  };
}
