import type {
  District,
  GoalTag,
  LearningCatalog,
  SpecializedSchool,
  SubjectTrack,
  TextbookSeries,
} from "@/services/catalog";
import type { TeachingMode, WeeklySlot } from "@/services/learningNeeds";
import type { TutorType } from "@/services/profile";

/**
 * Luật Lớp → Môn → (Nhánh) → Mục tiêu đọc từ danh mục backend. Backend kiểm tra lại đúng luật này,
 * nên bộ chọn chỉ hiển thị lựa chọn hợp lệ và không thể tạo tổ hợp sai.
 */

export function subjectHasTracks(catalog: LearningCatalog, subjectId: number | null | undefined): boolean {
  return subjectId != null && catalog.tracks.some((track) => track.subjectId === subjectId);
}

/** Nhánh của môn áp dụng cho khối lớp (rỗng khi môn không chia nhánh). */
export function tracksFor(
  catalog: LearningCatalog,
  subjectId: number | null | undefined,
  levelId: number | null | undefined
): SubjectTrack[] {
  if (subjectId == null) return [];
  return catalog.tracks.filter(
    (track) => track.subjectId === subjectId && (levelId == null || track.levelIds.includes(levelId))
  );
}

/** Mục tiêu hợp lệ cho tổ hợp, theo thứ tự danh mục. */
export function goalTagsFor(
  catalog: LearningCatalog,
  levelId: number | null | undefined,
  subjectId: number | null | undefined,
  trackId: number | null | undefined
): GoalTag[] {
  if (levelId == null || subjectId == null) return [];
  if (subjectHasTracks(catalog, subjectId) && trackId == null) return [];
  const allowed = new Set(
    catalog.goalTagRules
      .filter(
        (rule) =>
          rule.levelId === levelId && rule.subjectId === subjectId && (rule.trackId ?? null) === (trackId ?? null)
      )
      .map((rule) => rule.goalTagId)
  );
  return catalog.goalTags.filter((tag) => allowed.has(tag.goalTagId));
}

/** Mục tiêu áp dụng cho ít nhất một khối lớp của gói dạy (dùng cho gói dạy nhiều lớp). */
export function goalTagsForLevels(
  catalog: LearningCatalog,
  levelIds: number[],
  subjectId: number | null | undefined,
  trackId: number | null | undefined
): GoalTag[] {
  const ids = new Set(levelIds.flatMap((levelId) => goalTagsFor(catalog, levelId, subjectId, trackId).map((t) => t.goalTagId)));
  return catalog.goalTags.filter((tag) => ids.has(tag.goalTagId));
}

/** Khối lớp mà môn (+ nhánh) có ít nhất một mục tiêu. */
export function levelsFor(catalog: LearningCatalog, subjectId: number | null | undefined, trackId: number | null | undefined) {
  if (subjectId == null) return [];
  return catalog.educationLevels.filter((level) =>
    catalog.goalTagRules.some(
      (rule) => rule.levelId === level.levelId && rule.subjectId === subjectId && (rule.trackId ?? null) === (trackId ?? null)
    )
  );
}

export function textbooksFor(catalog: LearningCatalog, subjectId: number | null | undefined): TextbookSeries[] {
  if (subjectId == null) return [];
  return catalog.textbooks.filter((series) => series.subjectIds.includes(subjectId));
}

export function districtsOf(catalog: LearningCatalog, provinceId: number | null | undefined): District[] {
  if (provinceId == null) return [];
  return catalog.districts.filter((district) => district.provinceId === provinceId);
}

export function schoolsOf(catalog: LearningCatalog, provinceId: number | null | undefined): SpecializedSchool[] {
  return provinceId == null
    ? catalog.specializedSchools
    : catalog.specializedSchools.filter((school) => school.provinceId === provinceId);
}

export function goalTag(catalog: LearningCatalog, goalTagId: number | null | undefined): GoalTag | undefined {
  return catalog.goalTags.find((tag) => tag.goalTagId === goalTagId);
}

/** Mục tiêu cho phép chọn trường chuyên làm ngữ cảnh. */
export const GIFTED_TAG_CODE = "GIFTED";

export const MODE_LABELS: Record<TeachingMode, string> = {
  ONLINE: "Trực tuyến",
  OFFLINE: "Trực tiếp",
  BOTH: "Cả hai",
};

export const TUTOR_TYPE_LABELS: Record<TutorType, string> = {
  UNIVERSITY_STUDENT: "Sinh viên",
  GRADUATE: "Đã tốt nghiệp",
  IN_SERVICE_TEACHER: "Giáo viên đang dạy",
  RETIRED_TEACHER: "Giáo viên đã nghỉ hưu",
};

/** Tên hiển thị cho các trường còn thiếu của nhu cầu (missingFields từ backend). */
export const MISSING_FIELD_LABELS: Record<string, string> = {
  educationLevelId: "khối lớp",
  trackId: "nhánh môn",
  goalTagId: "mục tiêu",
  textbookSeriesId: "bộ sách",
  examProvinceId: "tỉnh dự thi",
  mode: "hình thức học",
  provinceId: "khu vực học trực tiếp",
  budgetMaxPerSession: "ngân sách mỗi buổi",
  availabilitySlots: "lịch rảnh",
};

export function describeMissing(fields: string[]): string {
  return fields.map((field) => MISSING_FIELD_LABELS[field] ?? field).join(", ");
}

const DAY_LABELS = ["", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];

export function formatSlot(slot: WeeklySlot): string {
  return `${DAY_LABELS[slot.dayOfWeek] ?? `Ngày ${slot.dayOfWeek}`} ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`;
}

export function formatMoney(amount: number | null | undefined): string {
  if (amount == null) return "—";
  return `${new Intl.NumberFormat("vi-VN").format(amount)}đ`;
}
