import { z } from "zod";

/**
 * Schema phản hồi yêu cầu kết nối từ gia sư.
 * - Khi accept = true: reason không bắt buộc, tối đa 300 ký tự.
 * - Khi accept = false: reason bắt buộc (tối thiểu 5 ký tự, tối đa 300 ký tự).
 */
export const respondRequestSchema = z
  .object({
    accept: z.boolean(),
    reason: z
      .string()
      .max(300, "Lý do không được vượt quá 300 ký tự")
      .optional()
      .or(z.literal("")),
  })
  .superRefine((data, ctx) => {
    if (!data.accept) {
      const trimmed = data.reason?.trim() || "";
      if (trimmed.length < 5) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)",
          path: ["reason"],
        });
      }
    }
  });

export type RespondRequestFormData = z.infer<typeof respondRequestSchema>;
