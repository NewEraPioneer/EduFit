import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { TutorVerificationBanner } from "@/features/verification/components/TutorVerificationBanner";
import TutorVerificationPage from "@/app/tutor/verification/page";
import { verificationService } from "@/services/verification";
import { profileService } from "@/services/profile";
import { ToastProvider } from "@/shared/ui/Toast";

vi.mock("@/services/verification", () => ({
  verificationService: {
    getMyRequest: vi.fn(),
    submitVerification: vi.fn(),
    getCredentialFileUrl: vi.fn((fileId: string) => `/api/v1/verifications/credentials/files/${fileId}`),
  },
}));

vi.mock("@/services/profile", () => ({
  profileService: {
    getMyTutorProfile: vi.fn(),
  },
}));

describe("Tutor Verification Supplementary Credentials", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("TutorVerificationBanner", () => {
    it("renders 'Bổ sung bằng cấp' button for APPROVED status and fires callback on click", () => {
      const onOpenUploadModal = vi.fn();
      render(
        <TutorVerificationBanner
          status="APPROVED"
          onOpenUploadModal={onOpenUploadModal}
          isProfileVerified={true}
        />
      );

      expect(screen.getByText("Hồ sơ gia sư đã được xác thực chính thức ✅")).toBeInTheDocument();
      const supplementBtn = screen.getByRole("button", { name: /Bổ sung bằng cấp/i });
      expect(supplementBtn).toBeInTheDocument();

      fireEvent.click(supplementBtn);
      expect(onOpenUploadModal).toHaveBeenCalledTimes(1);
    });

    it("renders 'Bổ sung bằng cấp' button for VERIFIED status", () => {
      const onOpenUploadModal = vi.fn();
      render(
        <TutorVerificationBanner
          status="VERIFIED"
          onOpenUploadModal={onOpenUploadModal}
          isProfileVerified={true}
        />
      );

      expect(screen.getByRole("button", { name: /Bổ sung bằng cấp/i })).toBeInTheDocument();
    });

    it("renders supplementary pending banner when status is PENDING and isProfileVerified is true", () => {
      render(
        <TutorVerificationBanner
          status="PENDING"
          onOpenUploadModal={vi.fn()}
          isProfileVerified={true}
        />
      );

      expect(screen.getByText("Hồ sơ gia sư đã được xác thực chính thức ✅")).toBeInTheDocument();
      expect(
        screen.getByText(/Bằng cấp bổ sung mới đang được Quản trị viên xét duyệt/i)
      ).toBeInTheDocument();
      expect(screen.getByText("Đang duyệt bằng bổ sung")).toBeInTheDocument();
      // Should not show regular pending notice
      expect(screen.queryByText("Hồ sơ đang chờ Quản trị viên xét duyệt ⏳")).not.toBeInTheDocument();
    });

    it("renders regular pending banner when status is PENDING and isProfileVerified is false", () => {
      render(
        <TutorVerificationBanner
          status="PENDING"
          onOpenUploadModal={vi.fn()}
          isProfileVerified={false}
        />
      );

      expect(screen.getByText("Hồ sơ đang chờ Quản trị viên xét duyệt ⏳")).toBeInTheDocument();
      expect(screen.queryByText("Đang duyệt bằng bổ sung")).not.toBeInTheDocument();
    });

    it("renders supplementary rejected banner and 'Nộp lại bằng bổ sung' when REJECTED with isProfileVerified true", () => {
      const onOpenUploadModal = vi.fn();
      render(
        <TutorVerificationBanner
          status="REJECTED"
          rejectionReason="Ảnh chứng chỉ IELTS bị mờ"
          onOpenUploadModal={onOpenUploadModal}
          isProfileVerified={true}
        />
      );

      expect(screen.getByText("Hồ sơ bổ sung bằng cấp chưa được duyệt ❌")).toBeInTheDocument();
      expect(screen.getByText("Ảnh chứng chỉ IELTS bị mờ")).toBeInTheDocument();
      const retryBtn = screen.getByRole("button", { name: /Nộp lại bằng bổ sung/i });
      expect(retryBtn).toBeInTheDocument();

      fireEvent.click(retryBtn);
      expect(onOpenUploadModal).toHaveBeenCalledTimes(1);
    });

    it("renders regular rejected banner when REJECTED and isProfileVerified is false", () => {
      render(
        <TutorVerificationBanner
          status="REJECTED"
          rejectionReason="Bằng tốt nghiệp không hợp lệ"
          onOpenUploadModal={vi.fn()}
          isProfileVerified={false}
        />
      );

      expect(screen.getByText("Hồ sơ xác minh chưa được duyệt ❌")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Nộp lại bằng cấp/i })).toBeInTheDocument();
    });
  });

  describe("TutorVerificationPage", () => {
    it("loads verification request and profile, displaying 'Bổ sung bằng cấp' for approved tutor", async () => {
      vi.mocked(verificationService.getMyRequest).mockResolvedValue({
        requestId: "req-app-1",
        tutorId: "tutor-1",
        status: "APPROVED",
        credentials: [
          {
            credentialId: "c1",
            type: "DEGREE",
            institution: "Đại học Bách Khoa",
            year: 2022,
            note: "Bằng cử nhân",
            files: [],
          },
        ],
      });

      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        id: "tutor-1",
        userId: "user-1",
        headline: "Gia sư Toán",
        bio: "Kinh nghiệm 5 năm",
        hourlyRate: 200000,
        status: "VERIFIED",
        verifiedAt: "2026-01-01T00:00:00Z",
      } as any);

      render(
        <ToastProvider>
          <TutorVerificationPage />
        </ToastProvider>
      );

      // Verify header and credential list
      await waitFor(() => {
        expect(screen.getByText("Đại học Bách Khoa")).toBeInTheDocument();
        expect(screen.getByText("Năm cấp bằng: 2022")).toBeInTheDocument();
      });

      // Verify "Bổ sung bằng cấp" button is present in page header
      const supplementBtns = screen.getAllByRole("button", { name: /Bổ sung bằng cấp/i });
      expect(supplementBtns.length).toBeGreaterThanOrEqual(1);

      // Click button and verify upload modal opens
      fireEvent.click(supplementBtns[0]);
      await waitFor(() => {
        expect(screen.getByText("Nộp hồ sơ xác minh gia sư (KYC)")).toBeInTheDocument();
      });
    });

    it("displays 'Đang xét duyệt bằng bổ sung' when tutor profile is VERIFIED but request is PENDING", async () => {
      vi.mocked(verificationService.getMyRequest).mockResolvedValue({
        requestId: "req-pend-2",
        tutorId: "tutor-1",
        status: "PENDING",
        credentials: [
          {
            credentialId: "c1",
            type: "DEGREE",
            institution: "Đại học Bách Khoa",
            year: 2022,
            note: "Bằng cử nhân",
            files: [],
          },
          {
            credentialId: "c2",
            type: "CERTIFICATE",
            institution: "British Council",
            year: 2024,
            note: "IELTS 8.0",
            files: [],
          },
        ],
      });

      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        id: "tutor-1",
        userId: "user-1",
        headline: "Gia sư Toán",
        bio: "Kinh nghiệm 5 năm",
        hourlyRate: 200000,
        status: "VERIFIED",
        verifiedAt: "2026-01-01T00:00:00Z",
      } as any);

      render(
        <ToastProvider>
          <TutorVerificationPage />
        </ToastProvider>
      );

      await waitFor(() => {
        expect(screen.getByText("Đang xét duyệt bằng bổ sung")).toBeInTheDocument();
        expect(screen.getByText("Đại học Bách Khoa")).toBeInTheDocument();
        expect(screen.getByText("British Council")).toBeInTheDocument();
      });
    });

    it("displays 'Nộp lại bằng bổ sung' in header and renders file links when request is REJECTED with isProfileVerified true", async () => {
      vi.mocked(verificationService.getMyRequest).mockResolvedValue({
        requestId: "req-rej-1",
        tutorId: "tutor-1",
        status: "REJECTED",
        rejectionReason: "Ảnh chứng chỉ bị mờ",
        credentials: [
          {
            credentialId: "c1",
            type: "DEGREE",
            institution: "Đại học Sư Phạm",
            year: 2021,
            note: "Cử nhân",
            files: [
              {
                fileId: "file-123",
                fileName: "bang_su_pham.pdf",
                storageKey: "key-123",
                contentType: "application/pdf",
                sizeBytes: 20480,
                uploadedAt: "2026-01-01T00:00:00Z",
              },
            ],
          },
        ],
      });

      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        id: "tutor-1",
        userId: "user-1",
        status: "VERIFIED",
        verifiedAt: "2026-01-01T00:00:00Z",
      } as any);

      render(
        <ToastProvider>
          <TutorVerificationPage />
        </ToastProvider>
      );

      await waitFor(() => {
        expect(screen.getByText("Đại học Sư Phạm")).toBeInTheDocument();
      });

      // Verify both banner and header have "Nộp lại bằng bổ sung" button
      const retryBtns = screen.getAllByRole("button", { name: /Nộp lại bằng bổ sung/i });
      expect(retryBtns.length).toBe(2);

      // Verify file link is rendered
      const fileLink = screen.getByRole("link", { name: /bang_su_pham\.pdf/i });
      expect(fileLink).toBeInTheDocument();
      expect(fileLink).toHaveAttribute("href", "/api/v1/verifications/credentials/files/file-123");
    });
  });
});
