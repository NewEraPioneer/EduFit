import { z } from "zod";
import {
  MAX_PLAN_MILESTONES,
  MAX_SUGGESTED_SESSIONS,
  MAX_TEMPLATE_TITLE_LENGTH,
  MIN_PLAN_MILESTONES,
  MIN_SUGGESTED_SESSIONS,
} from "../constants/roadmap";
import {
  EVIDENCE_NOTE_MAX_LENGTH,
  FEEDBACK_COMMENT_MAX_LENGTH,
  MAX_CRITERIA_PER_MILESTONE,
  PARENT_MESSAGE_MAX_LENGTH,
} from "../constants/feedbackLabels";

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

const criterionContentSchema = z
  .string()
  .trim()
  .min(1, { message: "Nội dung tiêu chí đạt không được để trống" })
  .max(200, { message: "Nội dung tiêu chí đạt tối đa 200 ký tự" });

/**
 * Schema cho từng cột mốc trong Kế hoạch học tập (Milestone).
 * Trọng số weight: 1 đến 5 (mặc định là 1). Bắt buộc 1–4 tiêu chí đạt.
 */
export const createMilestoneItemSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, { message: "Tiêu đề cột mốc không được để trống" })
    .max(200, { message: "Tiêu đề cột mốc tối đa 200 ký tự" }),
  description: z
    .string()
    .max(1000, { message: "Mô tả cột mốc tối đa 1000 ký tự" })
    .optional()
    .or(z.literal("")),
  targetDate: z.string().optional().or(z.literal("")),
  weight: z
    .number()
    .int({ message: "Trọng số phải là số nguyên" })
    .min(1, { message: "Trọng số tối thiểu là 1" })
    .max(5, { message: "Trọng số tối đa là 5" })
    .default(1),
  criteria: z
    .array(criterionContentSchema)
    .min(1, { message: "Mỗi cột mốc cần ít nhất 1 tiêu chí đạt" })
    .max(MAX_CRITERIA_PER_MILESTONE, { message: `Mỗi cột mốc có tối đa ${MAX_CRITERIA_PER_MILESTONE} tiêu chí đạt` }),
});

export type CreateMilestoneItemInput = z.infer<typeof createMilestoneItemSchema>;

/** Danh sách cột mốc của một lộ trình: 3–8 cột mốc, cột mốc cuối bắt buộc có hạn. */
function roadmapMilestones<T extends z.ZodType<{ targetDate?: string }>>(item: T) {
  return z
    .array(item)
    .min(MIN_PLAN_MILESTONES, { message: `Lộ trình cần ít nhất ${MIN_PLAN_MILESTONES} cột mốc` })
    .max(MAX_PLAN_MILESTONES, { message: `Lộ trình tối đa ${MAX_PLAN_MILESTONES} cột mốc` })
    .refine((items) => items.length === 0 || Boolean(items[items.length - 1]?.targetDate), {
      message: "Cột mốc cuối của lộ trình bắt buộc có hạn",
    });
}

/**
 * Schema tạo Kế hoạch học tập mới (Create Learning Plan).
 * Ràng buộc: 3–8 cột mốc; cột mốc cuối bắt buộc có hạn (các mốc giữa hệ thống nội suy).
 */
export const createPlanSchema = z.object({
  classId: z
    .string()
    .regex(UUID_REGEX, { message: "Mã lớp học không hợp lệ" }),
  summary: z
    .string()
    .max(1000, { message: "Tóm tắt kế hoạch tối đa 1000 ký tự" })
    .optional()
    .or(z.literal("")),
  milestones: roadmapMilestones(createMilestoneItemSchema),
});

export type CreatePlanInput = z.infer<typeof createPlanSchema>;

/**
 * Schema cập nhật từng cột mốc
 */
export const updateMilestoneItemSchema = createMilestoneItemSchema.extend({
  milestoneId: z.string().optional(),
  criteria: z
    .array(z.object({ criterionId: z.string().optional(), content: criterionContentSchema }))
    .max(MAX_CRITERIA_PER_MILESTONE, { message: `Mỗi cột mốc có tối đa ${MAX_CRITERIA_PER_MILESTONE} tiêu chí đạt` }),
});

export type UpdateMilestoneItemInput = z.infer<typeof updateMilestoneItemSchema>;

/**
 * Schema cập nhật Kế hoạch học tập
 */
export const updatePlanSchema = z.object({
  version: z.number().int({ message: "Phiên bản kế hoạch không hợp lệ" }),
  summary: z
    .string()
    .max(1000, { message: "Tóm tắt kế hoạch tối đa 1000 ký tự" })
    .optional()
    .or(z.literal("")),
  milestones: roadmapMilestones(updateMilestoneItemSchema),
});

export type UpdatePlanInput = z.infer<typeof updatePlanSchema>;

/**
 * Schema cập nhật trạng thái cột mốc (UC5.4)
 */
export const updateMilestoneStatusSchema = z
  .object({
    version: z.number().int({ message: "Phiên bản kế hoạch không hợp lệ" }),
    status: z.enum(["IN_PROGRESS", "ACHIEVED"], {
      message: "Trạng thái cột mốc không hợp lệ",
    }),
    achievedDate: z.string().optional().or(z.literal("")),
    reason: z.string().trim().max(300, { message: "Lý do mở lại tối đa 300 ký tự" }).optional(),
  })
  .refine((v) => v.status !== "IN_PROGRESS" || (v.reason?.length ?? 0) > 0, {
    message: "Cần ghi lý do khi mở lại cột mốc đã đạt",
    path: ["reason"],
  });

export type UpdateMilestoneStatusInput = z.infer<typeof updateMilestoneStatusSchema>;

/**
 * Schema ghi nhận kết quả và biên bản buổi học (UC4.6).
 * outcome: COMPLETED (Đã học / Có mặt) hoặc ABSENT (Vắng mặt).
 */
export const recordSessionOutcomeSchema = z.object({
  outcome: z.enum(["COMPLETED", "ABSENT"], {
    message: "Vui lòng chọn kết quả điểm danh (COMPLETED hoặc ABSENT)",
  }),
  noteContent: z
    .string()
    .max(FEEDBACK_COMMENT_MAX_LENGTH, { message: `Nhận xét tối đa ${FEEDBACK_COMMENT_MAX_LENGTH} ký tự` })
    .optional()
    .or(z.literal("")),
});

export type RecordSessionOutcomeInput = z.infer<typeof recordSessionOutcomeSchema>;

/**
 * Schema phản hồi 10 giây sau buổi học: mỗi cột mốc được dạy cần mức tự lập; "đúng x/y bài" tùy chọn
 * nhưng phải nhập đủ cả hai; chốt đạt chỉ khi tick đủ tiêu chí.
 */
export const taughtMilestoneSchema = z
  .object({
    milestoneId: z.string().regex(UUID_REGEX, { message: "Cột mốc không hợp lệ" }),
    level: z.union([z.literal(1), z.literal(2), z.literal(3), z.literal(4)], {
      message: "Cần chọn mức tự lập cho cột mốc được dạy",
    }),
    correctCount: z.number().int().min(0).optional(),
    totalCount: z.number().int().min(1).max(100).optional(),
    evidenceNote: z
      .string()
      .trim()
      .max(EVIDENCE_NOTE_MAX_LENGTH, { message: `Câu bằng chứng tối đa ${EVIDENCE_NOTE_MAX_LENGTH} ký tự` })
      .optional(),
    checkedCriterionIds: z.array(z.string()),
    totalCriteria: z.number().int().min(0),
    markAchieved: z.boolean(),
  })
  .refine((m) => (m.correctCount === undefined) === (m.totalCount === undefined), {
    message: "Cần nhập đủ cả số bài đúng và tổng số bài",
    path: ["correctCount"],
  })
  .refine((m) => m.correctCount === undefined || m.totalCount === undefined || m.correctCount <= m.totalCount, {
    message: "Số bài đúng không được lớn hơn tổng số bài",
    path: ["correctCount"],
  })
  .refine((m) => m.level !== 4 || m.checkedCriterionIds.length === m.totalCriteria, {
    message: "Chưa thể chấm mức 4 khi chưa tick đủ tiêu chí đạt",
    path: ["level"],
  })
  .refine((m) => !m.markAchieved || m.checkedCriterionIds.length === m.totalCriteria, {
    message: "Cần tick đủ tiêu chí đạt trước khi chốt cột mốc",
    path: ["markAchieved"],
  });

// Thứ tự trường khớp thứ tự hiển thị của biểu mẫu để lỗi đầu tiên là lỗi người dùng thấy trước
export const sessionFeedbackSchema = z.object({
  milestones: z
    .array(taughtMilestoneSchema)
    .min(1, { message: "Cần chọn ít nhất một cột mốc được dạy trong buổi" }),
  homeworkStatus: z.enum(["ASSIGNED", "NONE", "PREVIOUSLY_ASSIGNED"], {
    message: "Cần chọn chip việc về nhà",
  }),
  attitudeChips: z.array(
    z.enum(["FOCUSED", "ASKS_QUESTIONS", "HARD_WORKING", "NEEDS_MORE_FOCUS", "NEEDS_MORE_PRACTICE", "NEEDS_PREPARATION"])
  ),
  parentMessage: z
    .string()
    .trim()
    .max(PARENT_MESSAGE_MAX_LENGTH, {
      message: `Lời nhắn riêng cho phụ huynh tối đa ${PARENT_MESSAGE_MAX_LENGTH} ký tự`,
    })
    .optional(),
});

export type SessionFeedbackInput = z.infer<typeof sessionFeedbackSchema>;

/** Mẫu lộ trình: 3–8 cột mốc, mỗi cột mốc 2–6 buổi gợi ý và 1–4 tiêu chí đạt. */
export const planTemplateSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, { message: "Tên mẫu lộ trình không được để trống" })
    .max(MAX_TEMPLATE_TITLE_LENGTH, { message: `Tên mẫu lộ trình tối đa ${MAX_TEMPLATE_TITLE_LENGTH} ký tự` }),
  milestones: z
    .array(
      z.object({
        title: z.string().trim().min(1, { message: "Tiêu đề cột mốc không được để trống" }).max(200),
        description: z.string().max(1000).optional().nullable(),
        weight: z.number().int().min(1).max(5),
        suggestedSessions: z
          .number()
          .int()
          .min(MIN_SUGGESTED_SESSIONS, { message: `Mỗi cột mốc gợi ý từ ${MIN_SUGGESTED_SESSIONS} đến ${MAX_SUGGESTED_SESSIONS} buổi` })
          .max(MAX_SUGGESTED_SESSIONS, { message: `Mỗi cột mốc gợi ý từ ${MIN_SUGGESTED_SESSIONS} đến ${MAX_SUGGESTED_SESSIONS} buổi` }),
        criteria: z
          .array(criterionContentSchema)
          .min(1, { message: "Mỗi cột mốc cần ít nhất 1 tiêu chí đạt" })
          .max(MAX_CRITERIA_PER_MILESTONE, { message: `Mỗi cột mốc có tối đa ${MAX_CRITERIA_PER_MILESTONE} tiêu chí đạt` }),
      })
    )
    .min(MIN_PLAN_MILESTONES, { message: `Mẫu lộ trình cần ít nhất ${MIN_PLAN_MILESTONES} cột mốc` })
    .max(MAX_PLAN_MILESTONES, { message: `Mẫu lộ trình tối đa ${MAX_PLAN_MILESTONES} cột mốc` }),
});
