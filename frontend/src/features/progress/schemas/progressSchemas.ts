import { z } from "zod";

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/**
 * Schema cho từng cột mốc trong Kế hoạch học tập (Milestone).
 * Trọng số weight: 1 đến 5 (mặc định là 1).
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
});

export type CreateMilestoneItemInput = z.infer<typeof createMilestoneItemSchema>;

/**
 * Schema tạo Kế hoạch học tập mới (Create Learning Plan).
 * Ràng buộc: Có từ 1 đến 20 cột mốc.
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
  milestones: z
    .array(createMilestoneItemSchema)
    .min(1, { message: "Kế hoạch học tập phải có ít nhất 1 cột mốc" })
    .max(20, { message: "Kế hoạch học tập tối đa 20 cột mốc" }),
});

export type CreatePlanInput = z.infer<typeof createPlanSchema>;

/**
 * Schema cập nhật từng cột mốc
 */
export const updateMilestoneItemSchema = createMilestoneItemSchema.extend({
  milestoneId: z.string().optional(),
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
  milestones: z
    .array(updateMilestoneItemSchema)
    .min(1, { message: "Kế hoạch học tập phải có ít nhất 1 cột mốc" })
    .max(20, { message: "Kế hoạch học tập tối đa 20 cột mốc" }),
});

export type UpdatePlanInput = z.infer<typeof updatePlanSchema>;

/**
 * Schema cập nhật trạng thái cột mốc (UC5.4)
 */
export const updateMilestoneStatusSchema = z.object({
  version: z.number().int({ message: "Phiên bản kế hoạch không hợp lệ" }),
  status: z.enum(["NOT_STARTED", "IN_PROGRESS", "ACHIEVED"], {
    message: "Trạng thái cột mốc không hợp lệ",
  }),
  achievedDate: z.string().optional().or(z.literal("")),
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
    .max(5000, { message: "Nội dung nhận xét tối đa 5000 ký tự" })
    .optional()
    .or(z.literal("")),
  achievedMilestoneIds: z.array(z.string()).optional(),
});

export type RecordSessionOutcomeInput = z.infer<typeof recordSessionOutcomeSchema>;
