import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { ReviewKycModal } from "@/features/verification/components/ReviewKycModal";
import { UploadCredentialModal } from "@/features/verification/components/UploadCredentialModal";
import { verificationService, PendingQueueItem } from "@/services/verification";
import { ToastProvider } from "@/shared/ui/Toast";
import {
  validateCredentialFile,
  credentialUploadSchema,
  kycReviewSchema,
  MAX_FILE_SIZE,
} from "@/features/verification/schemas/kycSchemas";

vi.mock("@/services/verification", () => ({
  verificationService: {
    getPendingQueue: vi.fn(),
    getVerificationDetail: vi.fn(),
    reviewVerification: vi.fn(),
    submitVerification: vi.fn(),
    getCredentialFileUrl: vi.fn((fileId: string) => `/api/v1/verifications/credentials/files/${fileId}`),
  },
}));

describe("QA Usability Audit Suite - Phase 1: Hotfix P0 KYC & Upload (AC-01)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("AC-01.1: File Upload Size & Validation (2MB - 10MB Handling)", () => {
    it("cho phép tải tệp từ 2MB đến 10MB (ảnh chụp điện thoại độ phân giải cao)", () => {
      // 2MB JPG
      const file2MB = { name: "cccd_mat_truoc.jpg", size: 2 * 1024 * 1024 };
      const res2MB = validateCredentialFile(file2MB);
      expect(res2MB.valid).toBe(true);

      // 5MB PNG
      const file5MB = { name: "bang_dai_hoc_scan.png", size: 5 * 1024 * 1024 };
      const res5MB = validateCredentialFile(file5MB);
      expect(res5MB.valid).toBe(true);

      // 9.9MB PDF
      const file9MB = { name: "ho_so_chung_chi.pdf", size: 9.9 * 1024 * 1024 };
      const res9MB = validateCredentialFile(file9MB);
      expect(res9MB.valid).toBe(true);
    });

    it("chặn tệp vượt quá 10MB kèm thông báo người dùng thân thiện", () => {
      const file11MB = { name: "video_ho_so.pdf", size: 11 * 1024 * 1024 };
      const res11MB = validateCredentialFile(file11MB);
      expect(res11MB.valid).toBe(false);
      expect(res11MB.error).toContain("vượt quá giới hạn 10 MB");
    });

    it("chặn định dạng tệp nguy hiểm không phải PDF/JPG/PNG", () => {
      const badFile = { name: "credential.sh", size: 1024 };
      const res = validateCredentialFile(badFile);
      expect(res.valid).toBe(false);
      expect(res.error).toContain("không đúng định dạng PDF, JPG hoặc PNG");
    });
  });

  describe("AC-01.2: KYC OTHER (CCCD) & Nullable Graduation Year", () => {
    it("Zod schema chấp nhận loại giấy tờ OTHER với graduationYear để trống / undefined / null", () => {
      // Case 1: Không truyền graduationYear
      const parsed1 = credentialUploadSchema.safeParse({
        documentType: "OTHER",
        institutionName: "Bộ Công An - Cục Cảnh sát QLHC về TTXH",
      });
      expect(parsed1.success).toBe(true);
      if (parsed1.success) {
        expect(parsed1.data.graduationYear).toBeUndefined();
      }

      // Case 2: Truyền graduationYear là null
      const parsed2 = credentialUploadSchema.safeParse({
        documentType: "OTHER",
        institutionName: "Bộ Công An",
        graduationYear: null,
      });
      expect(parsed2.success).toBe(true);
      if (parsed2.success) {
        expect(parsed2.data.graduationYear).toBeNull();
      }

      // Case 3: Truyền chuỗi rỗng "" (từ HTML input rỗng)
      const parsed3 = credentialUploadSchema.safeParse({
        documentType: "OTHER",
        institutionName: "Bộ Công An",
        graduationYear: "",
      });
      expect(parsed3.success).toBe(true);
      if (parsed3.success) {
        expect(parsed3.data.graduationYear).toBeUndefined();
      }
    });

    it("UploadCredentialModal cho phép nộp giấy tờ OTHER mà không cần nhập năm tốt nghiệp", async () => {
      vi.mocked(verificationService.submitVerification).mockResolvedValue({
        requestId: "req-other-001",
        tutorId: "tutor-123",
        status: "PENDING",
        credentials: [],
      });

      const onSuccess = vi.fn();
      render(
        <ToastProvider>
          <UploadCredentialModal isOpen={true} onClose={vi.fn()} onSuccess={onSuccess} />
        </ToastProvider>
      );

      // Chọn loại giấy tờ OTHER
      const selectType = screen.getByRole("combobox");
      fireEvent.change(selectType, { target: { value: "OTHER" } });

      // Nhập đơn vị cấp
      const instInput = screen.getByPlaceholderText(/Ví dụ: Đại học Sư phạm Hà Nội/i);
      fireEvent.change(instInput, { target: { value: "Cục Cảnh sát QLHC về TTXH" } });

      // Để trống ô năm tốt nghiệp (kiểm tra placeholder thân thiện)
      const yearInput = screen.getByPlaceholderText(/Để trống nếu là CCCD/i);
      expect(yearInput).toHaveValue(null);

      // Tạo tệp giả lập 3MB
      const mockFile = new File(["dummy content".repeat(200000)], "can_cuoc_cong_dan.jpg", {
        type: "image/jpeg",
      });

      // Tải file lên
      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      Object.defineProperty(fileInput, "files", {
        value: [mockFile],
      });
      fireEvent.change(fileInput);

      // Chờ file được ghi nhận trong danh sách
      await waitFor(() => {
        expect(screen.getByText("can_cuoc_cong_dan.jpg")).toBeInTheDocument();
      });

      // Bấm nút Xác nhận nộp
      const submitBtn = screen.getByRole("button", { name: /Xác nhận nộp/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(verificationService.submitVerification).toHaveBeenCalledWith(
          expect.objectContaining({
            credentials: [
              expect.objectContaining({
                type: "OTHER",
                institution: "Cục Cảnh sát QLHC về TTXH",
                year: undefined,
              }),
            ],
          }),
          expect.any(Array)
        );
        expect(onSuccess).toHaveBeenCalled();
      });
    });
  });

  describe("AC-01.3 & AC-01.4: Admin KYC Review (APPROVE / REJECT) Contract Verification", () => {
    const mockPendingItem: PendingQueueItem = {
      requestId: "req-audit-777",
      tutorId: "tutor-audit-999",
      status: "PENDING",
      submittedAt: "2026-10-18T12:00:00Z",
    };

    it("Admin duyệt (APPROVE) gửi chính xác contract payload { action: 'APPROVE' }", async () => {
      vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
        requestId: "req-audit-777",
        tutorId: "tutor-audit-999",
        status: "PENDING",
        credentials: [
          {
            credentialId: "cred-1",
            type: "OTHER",
            institution: "Cục Cảnh sát QLHC về TTXH",
            note: "CCCD gắn chip",
            files: [
              {
                fileId: "f-cccd",
                fileName: "cccd.png",
                storageKey: "keys/cccd.png",
                contentType: "image/png",
                sizeBytes: 3145728, // 3MB
                uploadedAt: "2026-10-18T12:00:00Z",
              },
            ],
          },
        ],
      });

      vi.mocked(verificationService.reviewVerification).mockResolvedValue({
        requestId: "req-audit-777",
        tutorId: "tutor-audit-999",
        status: "APPROVED",
        credentials: [],
      });

      const onReviewed = vi.fn();
      const onClose = vi.fn();

      render(
        <ToastProvider>
          <ReviewKycModal
            item={mockPendingItem}
            isOpen={true}
            onClose={onClose}
            onReviewed={onReviewed}
          />
        </ToastProvider>
      );

      // Chờ nạp chi tiết
      await waitFor(() => {
        expect(screen.getByText("Cục Cảnh sát QLHC về TTXH")).toBeInTheDocument();
        expect(screen.getByText("cccd.png")).toBeInTheDocument();
        expect(screen.getByText("(3072 KB)")).toBeInTheDocument();
      });

      // Bấm nút Xác nhận quyết định (mặc định APPROVED)
      const submitBtn = screen.getByRole("button", { name: /Xác nhận quyết định/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(verificationService.reviewVerification).toHaveBeenCalledWith(
          "req-audit-777",
          "APPROVED",
          undefined,
          null
        );
        expect(onReviewed).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });

    it("Admin từ chối (REJECT) bắt buộc nhập lý do BR-06 và gửi payload { action: 'REJECT', reason }", async () => {
      vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
        requestId: "req-audit-777",
        tutorId: "tutor-audit-999",
        status: "PENDING",
        credentials: [],
      });

      vi.mocked(verificationService.reviewVerification).mockResolvedValue({
        requestId: "req-audit-777",
        tutorId: "tutor-audit-999",
        status: "REJECTED",
        rejectionReason: "Ảnh CCCD bị cắt góc, vui lòng chụp lại toàn bộ 4 góc",
        credentials: [],
      });

      render(
        <ToastProvider>
          <ReviewKycModal
            item={mockPendingItem}
            isOpen={true}
            onClose={vi.fn()}
            onReviewed={vi.fn()}
          />
        </ToastProvider>
      );

      // Chuyển sang nút TỪ CHỐI
      const rejectButton = screen.getByRole("button", { name: /TỪ CHỐI/i });
      fireEvent.click(rejectButton);

      // Textarea hiển thị
      const reasonInput = screen.getByPlaceholderText(/Ảnh bằng tốt nghiệp bị mờ/i);
      expect(reasonInput).toBeInTheDocument();

      // Nhập lý do từ chối
      fireEvent.change(reasonInput, {
        target: { value: "Ảnh CCCD bị cắt góc, vui lòng chụp lại toàn bộ 4 góc" },
      });

      // Submit
      const submitBtn = screen.getByRole("button", { name: /Xác nhận quyết định/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(verificationService.reviewVerification).toHaveBeenCalledWith(
          "req-audit-777",
          "REJECTED",
          "Ảnh CCCD bị cắt góc, vui lòng chụp lại toàn bộ 4 góc",
          null
        );
      });
    });
  });
});
