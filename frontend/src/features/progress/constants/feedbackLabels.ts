import type {
  AttitudeChip,
  HomeworkStatus,
  IndependenceLevel,
  MissedSessionKind,
  OnTrackStatus,
  SelfRatingValue,
} from "../types";

/** Thang mức độ tự lập (khớp backend IndependenceLevel). */
export const INDEPENDENCE_LEVELS: { value: IndependenceLevel; label: string; short: string }[] = [
  { value: 1, label: "Chưa làm được", short: "Chưa làm được" },
  { value: 2, label: "Làm được khi có gợi ý", short: "Cần gợi ý" },
  { value: 3, label: "Tự làm đúng", short: "Tự làm đúng" },
  { value: 4, label: "Tự làm đúng cả dạng khó và giải thích lại được", short: "Vững, giải thích được" },
];

export function independenceLabel(level?: number | null): string | null {
  return INDEPENDENCE_LEVELS.find((l) => l.value === level)?.label ?? null;
}

export const HOMEWORK_OPTIONS: { value: HomeworkStatus; label: string }[] = [
  { value: "ASSIGNED", label: "Có giao bài về nhà" },
  { value: "NONE", label: "Không có bài về nhà" },
  { value: "PREVIOUSLY_ASSIGNED", label: "Bài đã giao trước đó" },
];

/** Chip thái độ chỉ gồm lời mang tính xây dựng (học sinh và phụ huynh cùng xem). */
export const ATTITUDE_CHIPS: { value: AttitudeChip; label: string }[] = [
  { value: "FOCUSED", label: "Tập trung tốt" },
  { value: "ASKS_QUESTIONS", label: "Chủ động đặt câu hỏi" },
  { value: "HARD_WORKING", label: "Chăm chỉ, nỗ lực" },
  { value: "NEEDS_MORE_FOCUS", label: "Cần tập trung hơn" },
  { value: "NEEDS_MORE_PRACTICE", label: "Cần luyện thêm ở nhà" },
  { value: "NEEDS_PREPARATION", label: "Cần chuẩn bị bài trước" },
];

export function attitudeLabel(chip: AttitudeChip): string {
  return ATTITUDE_CHIPS.find((c) => c.value === chip)?.label ?? chip;
}

export function homeworkLabel(status: HomeworkStatus): string {
  return HOMEWORK_OPTIONS.find((h) => h.value === status)?.label ?? status;
}

export const ON_TRACK_LABELS: Record<OnTrackStatus, string> = {
  AHEAD: "Vượt tiến độ",
  ON_TRACK: "Đúng tiến độ",
  NEEDS_ATTENTION: "Cần chú ý",
  BEHIND: "Chậm",
  NOT_STARTED: "Chưa bắt đầu",
  NOT_ENOUGH_DATA: "Chưa đủ dữ liệu",
  NO_PLAN: "Chưa có lộ trình",
};

export const MISSED_SESSION_LABELS: Record<MissedSessionKind, string> = {
  ABSENT: "Vắng mặt",
  LATE_CANCEL_BY_FAMILY: "Gia đình hủy muộn (dưới 12 giờ)",
  EARLY_CANCEL_BY_FAMILY: "Gia đình hủy sớm (không trừ chuyên cần)",
  CANCELLED_BY_TUTOR: "Gia sư dời buổi (không trừ chuyên cần)",
  CANCELLED_OTHER: "Hủy do hệ thống",
};

/** Cảm nhận 1 chạm của học sinh (3 biểu tượng), độc lập với điểm chấm của gia sư. */
export const SELF_RATINGS: { value: SelfRatingValue; emoji: string; label: string }[] = [
  { value: 1, emoji: "😕", label: "Còn thấy khó" },
  { value: 2, emoji: "🙂", label: "Tạm ổn" },
  { value: 3, emoji: "😄", label: "Hiểu bài" },
];

export function selfRatingOption(value?: number | null) {
  return SELF_RATINGS.find((r) => r.value === value) ?? null;
}

/** Giới hạn ô nhận xét ngắn của biểu mẫu 10 giây. */
export const FEEDBACK_COMMENT_MAX_LENGTH = 140;
/** Giới hạn lời nhắn riêng cho phụ huynh (khớp backend SessionFeedback.MAX_PARENT_MESSAGE_LENGTH). */
export const PARENT_MESSAGE_MAX_LENGTH = 300;
/** Giới hạn câu bằng chứng của điểm chấm (khớp backend MilestoneAssessment.MAX_EVIDENCE_LENGTH). */
export const EVIDENCE_NOTE_MAX_LENGTH = 200;
export const MAX_CRITERIA_PER_MILESTONE = 4;
