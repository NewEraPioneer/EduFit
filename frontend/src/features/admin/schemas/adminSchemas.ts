import { z } from "zod";

export const createSubjectSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Tên môn học không được để trống")
    .max(100, "Tên môn học tối đa 100 ký tự"),
  category: z.string().trim().max(100, "Danh mục tối đa 100 ký tự").optional().or(z.literal("")),
  code: z.string().trim().max(50, "Mã môn học tối đa 50 ký tự").optional().or(z.literal("")),
  slug: z.string().trim().max(100, "Slug tối đa 100 ký tự").optional().or(z.literal("")),
  description: z.string().trim().max(500, "Mô tả tối đa 500 ký tự").optional().or(z.literal("")),
});

export type CreateSubjectFormValues = z.infer<typeof createSubjectSchema>;

export const auditLogFilterSchema = z.object({
  action: z.string().optional(),
  page: z.number().int().nonnegative().default(0),
  size: z.number().int().positive().max(100).default(20),
});
