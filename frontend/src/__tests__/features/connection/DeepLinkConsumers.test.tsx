import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";
import ParentChildrenPage from "@/app/parent/children/page";
import { TutorPendingRequests } from "@/features/connection/components/TutorPendingRequests";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { progressApi } from "@/features/progress";
import type { LinkInvitation, StudentLink, TutorInboxItem, InboxNeedSummary } from "@/features/connection/types";

const mockReplace = vi.fn();
let mockSearchParams = new URLSearchParams();
let mockPathname = "/student/family";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
    push: vi.fn(),
  }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => mockPathname,
}));

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: {
      id: "student-user-1",
      fullName: "Nguyễn Văn A",
      email: "a@edufit.vn",
      role: "STUDENT",
      status: "ACTIVE",
    },
    isLoading: false,
    error: null,
  }),
}));

const mockInvitation1: LinkInvitation = {
  invitationId: "inv-111",
  invitedUserId: "student-user-1",
  inviterUserId: "parent-user-1",
  inviterName: "Phụ huynh Lan",
  status: "PENDING",
  createdAt: "2026-10-18T00:00:00Z",
  expiresAt: "2026-12-31T00:00:00Z",
};

const mockInvitation2: LinkInvitation = {
  invitationId: "inv-222",
  invitedUserId: "student-user-1",
  inviterUserId: "parent-user-2",
  inviterName: "Phụ huynh Minh",
  status: "PENDING",
  createdAt: "2026-10-18T00:00:00Z",
  expiresAt: "2026-12-31T00:00:00Z",
};

const mockNeed: InboxNeedSummary = {
  educationLevelName: "Lớp 9",
  subjectName: "Toán học",
  trackName: null,
  goalTagName: "Luyện thi vào 10",
  textbookSeriesName: null,
  examProvinceName: null,
  targetSchoolName: null,
  mode: "ONLINE",
  provinceName: null,
  districtName: null,
  budgetMaxPerSession: 200000,
  availabilitySlots: [],
};

const mockRequest1: TutorInboxItem = {
  requestId: "req-111",
  desiredSlots: [],
  senderRole: "STUDENT",
  status: "PENDING",
  expiresAt: "2026-12-31T00:00:00Z",
  studentDisplayName: "Em Hoàng",
  need: mockNeed,
  offeringName: "Toán 9",
  fitChecks: [],
  hasSlotConflict: false,
  conflictingSlots: [],
};

const mockRequest2: TutorInboxItem = {
  requestId: "req-222",
  desiredSlots: [],
  senderRole: "PARENT",
  status: "PENDING",
  expiresAt: "2026-12-31T00:00:00Z",
  studentDisplayName: "Em Tuấn",
  need: { ...mockNeed, subjectName: "Vật lý" },
  offeringName: "Lý 9",
  fitChecks: [],
  hasSlotConflict: false,
  conflictingSlots: [],
};

describe("Deep Link Consumers (invitationId & requestId)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockReplace.mockReset();
    mockSearchParams = new URLSearchParams();
    mockPathname = "/student/family";

    vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([mockInvitation1, mockInvitation2]);
    vi.spyOn(connectionApi, "getLinks").mockResolvedValue([]);
    vi.spyOn(connectionApi, "getTutorInbox").mockResolvedValue([mockRequest1, mockRequest2]);
    vi.spyOn(progressApi, "getParentDashboard").mockResolvedValue({
      success: true,
      data: { students: [] },
    } as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("StudentLinkInvitations - consumer invitationId", () => {
    it("highlight thẻ lời mời khớp với invitationId trên query param và xóa param qua router.replace", async () => {
      mockPathname = "/student/family";
      mockSearchParams = new URLSearchParams("invitationId=inv-111");

      render(<StudentLinkInvitations variant="full" />);

      const card1 = await screen.findByTestId("invitation-card-inv-111");
      const card2 = await screen.findByTestId("invitation-card-inv-222");

      // Thẻ 1 được highlight với border/ring amber
      expect(card1.className).toContain("ring-2 ring-action bg-action-soft/50");
      // Thẻ 2 không được highlight
      expect(card2.className).not.toContain("ring-2 ring-action");

      // Tham số query được xóa bằng router.replace
      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith("/student/family", { scroll: false });
      });
    });

    it("hoạt động bình thường khi invitationId không tồn tại trong danh sách", async () => {
      mockPathname = "/student/family";
      mockSearchParams = new URLSearchParams("invitationId=non-existent");

      render(<StudentLinkInvitations variant="full" />);

      const card1 = await screen.findByTestId("invitation-card-inv-111");
      const card2 = await screen.findByTestId("invitation-card-inv-222");

      expect(card1.className).not.toContain("ring-2 ring-action");
      expect(card2.className).not.toContain("ring-2 ring-action");

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith("/student/family", { scroll: false });
      });
    });
  });

  describe("ParentChildrenPage - consumer invitationId", () => {
    it("highlight thẻ lời mời của phụ huynh khi có invitationId trên query param", async () => {
      mockPathname = "/parent/children";
      mockSearchParams = new URLSearchParams("invitationId=inv-111");

      // Parent là inviterUserId
      vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([
        { ...mockInvitation1, inviterUserId: "student-user-1" },
        { ...mockInvitation2, inviterUserId: "student-user-1" },
      ]);

      render(<ParentChildrenPage />);

      const card1 = await screen.findByTestId("invitation-card-inv-111");
      const card2 = await screen.findByTestId("invitation-card-inv-222");

      expect(card1.className).toContain("ring-2 ring-action bg-action-soft/50");
      expect(card2.className).not.toContain("ring-2 ring-action");

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith("/parent/children", { scroll: false });
      });
    });
  });

  describe("TutorPendingRequests - consumer requestId", () => {
    it("highlight thẻ yêu cầu khớp với requestId trên query param và xóa param qua router.replace", async () => {
      mockPathname = "/tutor/requests";
      mockSearchParams = new URLSearchParams("requestId=req-111");

      render(<TutorPendingRequests />);

      const card1 = await screen.findByTestId("request-card-req-111");
      const card2 = await screen.findByTestId("request-card-req-222");

      // Thẻ 1 được highlight với border/ring blue
      expect(card1.className).toContain("ring-2 ring-brand bg-brand-soft/40");
      // Thẻ 2 không được highlight
      expect(card2.className).not.toContain("ring-2 ring-brand");

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith("/tutor/requests", { scroll: false });
      });
    });

    it("hoạt động bình thường khi requestId không tồn tại", async () => {
      mockPathname = "/tutor/requests";
      mockSearchParams = new URLSearchParams("requestId=non-existent");

      render(<TutorPendingRequests />);

      const card1 = await screen.findByTestId("request-card-req-111");
      const card2 = await screen.findByTestId("request-card-req-222");

      expect(card1.className).not.toContain("ring-2 ring-brand");
      expect(card2.className).not.toContain("ring-2 ring-brand");

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith("/tutor/requests", { scroll: false });
      });
    });
  });
});
