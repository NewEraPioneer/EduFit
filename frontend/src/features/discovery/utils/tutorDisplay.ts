import type { CatalogRef, TutorOffering } from "@/services/profile";
import type { TeachingMode } from "../types";

export const TEACHING_MODE_TEXT: Record<TeachingMode, string> = {
  ONLINE: "Online",
  OFFLINE: "Trực tiếp",
  BOTH: "Online & trực tiếp",
};

/** "Toán", hoặc "Tiếng Anh – Thí điểm" khi gói dạy có nhánh. */
export function offeringName(offering: Pick<TutorOffering, "subjectName" | "trackName">): string {
  return offering.trackName ? `${offering.subjectName} – ${offering.trackName}` : offering.subjectName;
}

/** "Toán · Lớp 6–9" cho từng gói dạy, nối bằng dấu phẩy. */
export function offeringsSummary(offerings: TutorOffering[]): string {
  if (offerings.length === 0) return "Chưa cập nhật môn dạy";
  return offerings
    .map((offering) => {
      const levels = offering.levels.map((level) => level.name);
      const range = levels.length > 1 ? `${levels[0]}–${levels.at(-1)?.replace(/^Lớp\s*/i, "")}` : levels[0];
      return range ? `${offeringName(offering)} · ${range}` : offeringName(offering);
    })
    .join(", ");
}

/** Nơi dạy trực tiếp; gia sư chỉ dạy online thì không cần khu vực. */
export function tutorAreaText(tutor: {
  teachingMode: TeachingMode;
  provinceName: string | null;
  districts: CatalogRef[];
}): string {
  if (tutor.teachingMode === "ONLINE") return "Toàn quốc";
  if (!tutor.provinceName) return "Chưa cập nhật khu vực";
  if (tutor.districts.length === 0) return tutor.provinceName;
  return `${tutor.districts.map((district) => district.name).join(", ")} (${tutor.provinceName})`;
}

export function formatVnd(amount: number | null | undefined): string {
  if (amount == null) return "Chưa có học phí";
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);
}

export function formatRating(ratingAvg: number | null, reviewCount: number | null): string {
  if (ratingAvg == null || !reviewCount) return "Chưa có đánh giá";
  return `${ratingAvg.toFixed(1)} (${reviewCount})`;
}
