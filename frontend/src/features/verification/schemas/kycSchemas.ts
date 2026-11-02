import { z } from "zod";

export const ALLOWED_EXTENSIONS = [".pdf", ".jpg", ".jpeg", ".png"] as const;
export const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

export const CREDENTIAL_TYPES = [
  "DEGREE",
  "CERTIFICATE",
  "TEACHING_LICENCE",
  "STUDENT_CARD",
  "OTHER",
] as const;

export const CREDENTIAL_TYPE_LABELS: Record<string, string> = {
  DEGREE: "Bằng cấp",
  CERTIFICATE: "Chứng chỉ",
  TEACHING_LICENCE: "Chứng chỉ sư phạm",
  STUDENT_CARD: "Thẻ sinh viên",
  OTHER: "Giấy tờ khác / CCCD",
  IDENTITY_CARD: "Giấy tờ khác / CCCD",
};

export const credentialTypeSchema = z.enum([
  "DEGREE",
  "CERTIFICATE",
  "TEACHING_LICENCE",
  "STUDENT_CARD",
  "OTHER",
  "IDENTITY_CARD",
]);

export const credentialUploadSchema = z.object({
  documentType: credentialTypeSchema,
  institutionName: z
    .string()
    .trim()
    .min(2, "Tên trường / đơn vị cấp bằng tối thiểu 2 ký tự"),
  graduationYear: z
    .preprocess(
      (val) => {
        if (
          val === "" ||
          val === null ||
          val === undefined ||
          (typeof val === "number" && (Number.isNaN(val) || val === 0))
        ) {
          return undefined;
        }
        return typeof val === "string" ? Number(val) : val;
      },
      z
        .number()
        .int("Năm tốt nghiệp phải là số nguyên")
        .min(1970, "Năm tốt nghiệp không thể trước 1970")
        .max(new Date().getFullYear() + 10, "Năm tốt nghiệp không hợp lệ")
        .optional()
        .nullable()
    )
    .optional()
    .nullable(),
  notes: z
    .string()
    .trim()
    .max(500, "Ghi chú không được vượt quá 500 ký tự")
    .optional(),
});

export const kycReviewSchema = z
  .object({
    decision: z.enum(["APPROVED", "REJECTED", "APPROVE", "REJECT"]).optional(),
    action: z.enum(["APPROVE", "REJECT", "APPROVED", "REJECTED"]).optional(),
    rejectionReason: z.string().trim().optional(),
    reason: z.string().trim().optional(),
  })
  .refine(
    (data) => {
      const isReject =
        data.decision === "REJECTED" ||
        data.decision === "REJECT" ||
        data.action === "REJECT" ||
        data.action === "REJECTED";
      const rejectionReason = data.reason || data.rejectionReason;
      if (isReject) {
        return Boolean(rejectionReason && rejectionReason.trim().length >= 5);
      }
      return true;
    },
    {
      message: "Vui lòng nhập lý do từ chối rõ ràng (tối thiểu 5 ký tự)",
      path: ["rejectionReason"],
    }
  )
  .refine(
    (data) => Boolean(data.decision || data.action),
    {
      message: "Hành động duyệt hồ sơ là bắt buộc",
      path: ["action"],
    }
  );

export const revokeKycSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(5, "Vui lòng nhập lý do thu hồi rõ ràng (tối thiểu 5 ký tự)"),
});

export type RevokeKycInput = z.infer<typeof revokeKycSchema>;

export function validateCredentialFile(file: { name: string; size: number }): {
  valid: boolean;
  error?: string;
} {
  const lowerName = file.name.toLowerCase();
  const hasValidExt = ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext));
  if (!hasValidExt) {
    return {
      valid: false,
      error: `Tệp ${file.name} không đúng định dạng PDF, JPG hoặc PNG.`,
    };
  }
  if (file.size > MAX_FILE_SIZE) {
    return {
      valid: false,
      error: `Tệp ${file.name} vượt quá giới hạn 10 MB.`,
    };
  }
  return { valid: true };
}
