import { describe, it, expect } from "vitest";
import {
  credentialUploadSchema,
  kycReviewSchema,
  revokeKycSchema,
  validateCredentialFile,
  MAX_FILE_SIZE,
} from "@/features/verification/schemas/kycSchemas";

describe("KYC Zod Schemas & File Validation Tests", () => {
  describe("credentialUploadSchema", () => {
    it("chấp nhận thông tin nộp bằng cấp hợp lệ", () => {
      const result = credentialUploadSchema.safeParse({
        documentType: "DEGREE",
        institutionName: "Đại học Sư phạm Hà Nội",
        graduationYear: 2023,
        notes: "Tốt nghiệp loại Giỏi",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.institutionName).toBe("Đại học Sư phạm Hà Nội");
        expect(result.data.graduationYear).toBe(2023);
      }
    });

    it("hỗ trợ đầy đủ 5 loại giấy tờ: DEGREE, CERTIFICATE, TEACHING_LICENCE, STUDENT_CARD, OTHER", () => {
      const types = ["DEGREE", "CERTIFICATE", "TEACHING_LICENCE", "STUDENT_CARD", "OTHER"] as const;
      types.forEach((type) => {
        const result = credentialUploadSchema.safeParse({
          documentType: type,
          institutionName: "Tổ chức kiểm định giáo dục",
          graduationYear: 2022,
        });
        expect(result.success).toBe(true);
      });
    });

    it("chấp nhận giấy tờ OTHER (CCCD) để trống hoặc không có graduationYear", () => {
      const resultWithoutYear = credentialUploadSchema.safeParse({
        documentType: "OTHER",
        institutionName: "Cục Cảnh sát QLHC về TTXH",
      });
      expect(resultWithoutYear.success).toBe(true);
      if (resultWithoutYear.success) {
        expect(resultWithoutYear.data.graduationYear).toBeUndefined();
      }

      const resultWithNull = credentialUploadSchema.safeParse({
        documentType: "OTHER",
        institutionName: "Cục Cảnh sát QLHC về TTXH",
        graduationYear: null,
      });
      expect(resultWithNull.success).toBe(true);

      const resultWithEmptyString = credentialUploadSchema.safeParse({
        documentType: "OTHER",
        institutionName: "Cục Cảnh sát QLHC về TTXH",
        graduationYear: "",
      });
      expect(resultWithEmptyString.success).toBe(true);
    });

    it("chấp nhận chứng chỉ CERTIFICATE hoặc TEACHING_LICENCE không có năm tốt nghiệp", () => {
      const result = credentialUploadSchema.safeParse({
        documentType: "CERTIFICATE",
        institutionName: "IELTS British Council",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.graduationYear).toBeUndefined();
      }
    });

    it("từ chối loại tài liệu không hợp lệ", () => {
      const result = credentialUploadSchema.safeParse({
        documentType: "INVALID_DOC_TYPE",
        institutionName: "Đại học Sư phạm",
        graduationYear: 2022,
      });
      expect(result.success).toBe(false);
    });

    it("từ chối khi tên trường dưới 2 ký tự", () => {
      const result = credentialUploadSchema.safeParse({
        documentType: "DEGREE",
        institutionName: "A",
        graduationYear: 2023,
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Tên trường / đơn vị cấp bằng tối thiểu 2 ký tự"
        );
      }
    });

    it("từ chối khi năm tốt nghiệp trước 1970", () => {
      const result = credentialUploadSchema.safeParse({
        documentType: "CERTIFICATE",
        institutionName: "IELTS British Council",
        graduationYear: 1960,
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Năm tốt nghiệp không thể trước 1970"
        );
      }
    });
  });

  describe("kycReviewSchema", () => {
    it("chấp nhận phê duyệt hồ sơ (APPROVED hoặc action: APPROVE)", () => {
      const resultDecision = kycReviewSchema.safeParse({
        decision: "APPROVED",
      });
      expect(resultDecision.success).toBe(true);

      const resultAction = kycReviewSchema.safeParse({
        action: "APPROVE",
      });
      expect(resultAction.success).toBe(true);
    });

    it("chấp nhận từ chối hồ sơ kèm lý do đầy đủ (rejectionReason hoặc reason)", () => {
      const resultDecision = kycReviewSchema.safeParse({
        decision: "REJECTED",
        rejectionReason: "Ảnh chụp bằng cấp bị mờ, không rõ con dấu",
      });
      expect(resultDecision.success).toBe(true);

      const resultAction = kycReviewSchema.safeParse({
        action: "REJECT",
        reason: "Ảnh chụp bằng cấp bị mờ, không rõ số hiệu văn bằng",
      });
      expect(resultAction.success).toBe(true);
    });

    it("từ chối khi REJECTED nhưng thiếu lý do hoặc lý do quá ngắn", () => {
      const result = kycReviewSchema.safeParse({
        decision: "REJECTED",
        rejectionReason: "Mờ",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Vui lòng nhập lý do từ chối rõ ràng (tối thiểu 5 ký tự)"
        );
      }

      const resultAction = kycReviewSchema.safeParse({
        action: "REJECT",
        reason: "Mờ",
      });
      expect(resultAction.success).toBe(false);
    });

    it("từ chối khi không truyền action hoặc decision", () => {
      const result = kycReviewSchema.safeParse({});
      expect(result.success).toBe(false);
    });
  });

  describe("validateCredentialFile", () => {
    it("chấp nhận tệp PDF, JPG, PNG hợp lệ và dung lượng < 10MB", () => {
      expect(validateCredentialFile({ name: "bang_dai_hoc.pdf", size: 1024 * 1024 }).valid).toBe(true);
      expect(validateCredentialFile({ name: "chung_chi.jpg", size: 2 * 1024 * 1024 }).valid).toBe(true);
      expect(validateCredentialFile({ name: "cccd.png", size: 500 * 1024 }).valid).toBe(true);
    });

    it("từ chối định dạng tệp không được hỗ trợ", () => {
      const res = validateCredentialFile({ name: "virus.exe", size: 1024 });
      expect(res.valid).toBe(false);
      expect(res.error).toContain("không đúng định dạng PDF, JPG hoặc PNG");
    });

    it("từ chối tệp vượt quá 10MB", () => {
      const res = validateCredentialFile({ name: "tai_lieu_lon.pdf", size: MAX_FILE_SIZE + 1 });
      expect(res.valid).toBe(false);
      expect(res.error).toContain("vượt quá giới hạn 10 MB");
    });
  });

  describe("revokeKycSchema", () => {
    it("chấp nhận lý do thu hồi hợp lệ (>= 5 ký tự)", () => {
      const result = revokeKycSchema.safeParse({
        reason: "Phát hiện bằng cấp không hợp lệ qua kiểm tra thực tế",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.reason).toBe(
          "Phát hiện bằng cấp không hợp lệ qua kiểm tra thực tế"
        );
      }
    });

    it("từ chối khi lý do thu hồi bị rỗng hoặc dưới 5 ký tự", () => {
      const emptyResult = revokeKycSchema.safeParse({
        reason: "",
      });
      expect(emptyResult.success).toBe(false);

      const shortResult = revokeKycSchema.safeParse({
        reason: "abcd",
      });
      expect(shortResult.success).toBe(false);
      if (!shortResult.success) {
        expect(shortResult.error.issues[0].message).toContain(
          "tối thiểu 5 ký tự"
        );
      }
    });

    it("tự động trim khoảng trắng đầu cuối của lý do thu hồi", () => {
      const result = revokeKycSchema.safeParse({
        reason: "   Bằng cấp đã hết hiệu lực   ",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.reason).toBe("Bằng cấp đã hết hiệu lực");
      }
    });
  });
});
