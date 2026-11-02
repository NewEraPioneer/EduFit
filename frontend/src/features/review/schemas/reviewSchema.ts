import { z } from "zod";

export const reviewFormSchema = z.object({
  rating: z
    .number({
      message: "Vui lòng chọn số sao đánh giá",
    })
    .int()
    .min(1, "Điểm đánh giá tối thiểu là 1 sao")
    .max(5, "Điểm đánh giá tối đa là 5 sao"),
  comment: z
    .string({
      message: "Nội dung nhận xét không được để trống",
    })
    .trim()
    .min(10, "Nội dung nhận xét phải có ít nhất 10 ký tự")
    .max(1000, "Nội dung nhận xét không được vượt quá 1000 ký tự"),
  tags: z.array(z.string()).optional(),
});

export type ReviewFormValues = z.infer<typeof reviewFormSchema>;

