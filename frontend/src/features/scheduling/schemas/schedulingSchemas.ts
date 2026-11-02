import { z } from "zod";

export const sessionModeSchema = z.enum(["ONLINE", "OFFLINE"], {
  message: "Hình thức học phải là ONLINE hoặc OFFLINE",
});

export const cancelReasonSchema = z.enum(
  ["SCHEDULE_CONFLICT", "PERSONAL_REASON", "OTHER_PARTY_UNAVAILABLE", "OTHER"],
  {
    message: "Vui lòng chọn lý do hủy buổi học hợp lệ",
  }
);

export const proposeSessionSchema = z
  .object({
    classId: z.string().uuid("Vui lòng chọn lớp học hợp lệ"),
    tutorId: z.string().optional(),
    studentId: z.string().optional(),
    startAt: z.string().min(1, "Thời gian bắt đầu không được để trống"),
    endAt: z.string().min(1, "Thời gian kết thúc không được để trống"),
    mode: sessionModeSchema,
    placeOrLink: z
      .string()
      .max(255, "Địa điểm hoặc liên kết không được vượt quá 255 ký tự")
      .optional()
      .default(""),
    repeatNote: z
      .string()
      .max(255, "Ghi chú lặp lại không được vượt quá 255 ký tự")
      .optional()
      .default(""),
    message: z
      .string()
      .max(500, "Lời nhắn không được vượt quá 500 ký tự")
      .optional()
      .default(""),
  })
  .refine(
    (data) => {
      const start = new Date(data.startAt).getTime();
      const end = new Date(data.endAt).getTime();
      return !Number.isNaN(start) && !Number.isNaN(end) && end > start;
    },
    {
      message: "Thời gian kết thúc phải diễn ra sau thời gian bắt đầu",
      path: ["endAt"],
    }
  );

export type ProposeSessionInput = z.infer<typeof proposeSessionSchema>;

export const rescheduleSessionSchema = z
  .object({
    newStartAt: z.string().min(1, "Thời gian bắt đầu mới không được để trống"),
    newEndAt: z.string().min(1, "Thời gian kết thúc mới không được để trống"),
    reason: z
      .string()
      .max(500, "Lý do đổi lịch không được vượt quá 500 ký tự")
      .optional()
      .default(""),
  })
  .refine(
    (data) => {
      const start = new Date(data.newStartAt).getTime();
      const end = new Date(data.newEndAt).getTime();
      return !Number.isNaN(start) && !Number.isNaN(end) && end > start;
    },
    {
      message: "Thời gian kết thúc mới phải sau thời gian bắt đầu",
      path: ["newEndAt"],
    }
  );

export type RescheduleSessionInput = z.infer<typeof rescheduleSessionSchema>;

export const cancelSessionSchema = z.object({
  reason: cancelReasonSchema,
  comment: z
    .string()
    .max(500, "Ghi chú giải thích không được vượt quá 500 ký tự")
    .optional()
    .default(""),
});

export type CancelSessionInput = z.infer<typeof cancelSessionSchema>;
