import { describe, it, expect, vi, beforeEach } from "vitest";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { apiClient } from "@/lib/api";
import { makeInboxItem } from "../../fixtures/learningNeeds";

describe("connectionApi client", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("gọi đúng các endpoint lời mời và hủy liên kết gia đình", async () => {
    const invitation = {
      invitationId: "inv-1", inviterUserId: "parent-1", invitedUserId: "student-1",
      status: "PENDING", createdAt: "2026-10-18T00:00:00Z", expiresAt: "2026-11-01T00:00:00Z",
    };
    const get = vi.spyOn(apiClient, "get").mockResolvedValue({ success: true, data: [invitation] });
    const post = vi.spyOn(apiClient, "post").mockResolvedValue({ success: true, data: null });
    const remove = vi.spyOn(apiClient, "delete").mockResolvedValue({ success: true });

    expect(await connectionApi.getLinkInvitations()).toEqual([invitation]);
    await connectionApi.respondLinkInvitation("inv-1", true);
    await connectionApi.respondLinkInvitation("inv-1", false);
    await connectionApi.withdrawLinkInvitation("inv-1");
    await connectionApi.revokeLink("link-1");
    await connectionApi.revokeLink("link-2", "Lý do cá nhân");

    expect(get).toHaveBeenCalledWith("/api/v1/connections/parent-student/invitations");
    expect(post).toHaveBeenCalledWith("/api/v1/connections/parent-student/respond", { invitationId: "inv-1", accept: true });
    expect(post).toHaveBeenCalledWith("/api/v1/connections/parent-student/respond", { invitationId: "inv-1", accept: false });
    expect(post).toHaveBeenCalledWith("/api/v1/connections/parent-student/invitations/inv-1/withdraw");
    expect(remove).toHaveBeenCalledWith("/api/v1/connections/parent-student/links/link-1");
    expect(remove).toHaveBeenCalledWith("/api/v1/connections/parent-student/links/link-2", { body: JSON.stringify({ reason: "Lý do cá nhân" }) });
  });

  it("getTutorInbox gọi GET /api/v1/connections/requests khi không truyền phân trang", async () => {
    const mockInbox = [
      makeInboxItem({ requestId: "req-1", desiredSlots: ["Mon 19:00"], studentDisplayName: "Minh" }),
    ];

    const getSpy = vi.spyOn(apiClient, "get").mockResolvedValue({
      success: true,
      data: mockInbox,
    });

    const res = await connectionApi.getTutorInbox();
    expect(getSpy).toHaveBeenCalledWith("/api/v1/connections/requests");
    expect(res).toEqual(mockInbox);
  });

  it("getTutorInbox gọi GET /api/v1/connections/requests/page khi có phân trang", async () => {
    const mockPage = {
      items: [
        makeInboxItem({ requestId: "req-2", desiredSlots: ["Tue 18:00"], senderRole: "PARENT" }),
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    };

    const getSpy = vi.spyOn(apiClient, "get").mockResolvedValue({
      success: true,
      data: mockPage,
    });

    const res = await connectionApi.getTutorInbox(0, 10);
    expect(getSpy).toHaveBeenCalledWith("/api/v1/connections/requests/page?page=0&size=10");
    expect(res).toEqual(mockPage.items);
  });

  it("respondRequest gọi POST /api/v1/connections/requests/{id}/respond để chấp thuận và tạo lớp", async () => {
    const mockClassResult = {
      classId: "cls-100",
      studentId: "stu-1",
      tutorId: "tut-1",
      needId: "need-1",
    };

    const postSpy = vi.spyOn(apiClient, "post").mockResolvedValue({
      success: true,
      data: mockClassResult,
    });

    const res = await connectionApi.respondRequest("req-1", {
      accept: true,
      reason: "Đồng ý nhận lớp",
    });

    expect(postSpy).toHaveBeenCalledWith("/api/v1/connections/requests/req-1/respond", {
      accept: true,
      reason: "Đồng ý nhận lớp",
    });
    expect(res).toEqual(mockClassResult);
  });

  it("respondRequest gọi POST /api/v1/connections/requests/{id}/respond để từ chối", async () => {
    const postSpy = vi.spyOn(apiClient, "post").mockResolvedValue({
      success: true,
      data: null,
    });

    const res = await connectionApi.respondRequest("req-1", {
      accept: false,
      reason: "Lịch bận không thể nhận thêm",
    });

    expect(postSpy).toHaveBeenCalledWith("/api/v1/connections/requests/req-1/respond", {
      accept: false,
      reason: "Lịch bận không thể nhận thêm",
    });
    expect(res).toBeNull();
  });

  it("getTutorClasses gọi GET /api/v1/connections/classes", async () => {
    const mockClasses = [
      {
        classId: "cls-1",
        studentId: "stu-1",
        tutorId: "tut-1",
        needId: "need-1",
        status: "ACTIVE",
        studentDisplayName: "Minh",
        scheduleStatus: "PROPOSED",
        sessionCount: 0,
        startedAt: "2026-10-18T00:00:00Z",
      },
    ];

    const getSpy = vi.spyOn(apiClient, "get").mockResolvedValue({
      success: true,
      data: mockClasses,
    });

    const res = await connectionApi.getTutorClasses();
    expect(getSpy).toHaveBeenCalledWith("/api/v1/connections/classes");
    expect(res).toEqual(mockClasses);
  });

  it("getStudentClasses gọi GET /api/v1/connections/classes", async () => {
    const mockClasses = [
      {
        classId: "cls-2",
        studentId: "stu-2",
        tutorId: "tut-2",
        needId: "need-2",
        status: "ACTIVE",
        tutorDisplayName: "Thầy Hưng",
        scheduleStatus: "SCHEDULED",
        sessionCount: 4,
        startedAt: "2026-10-10T00:00:00Z",
      },
    ];

    const getSpy = vi.spyOn(apiClient, "get").mockResolvedValue({
      success: true,
      data: mockClasses,
    });

    const res = await connectionApi.getStudentClasses();
    expect(getSpy).toHaveBeenCalledWith("/api/v1/connections/classes");
    expect(res).toEqual(mockClasses);
  });

  it("cancelRequest gọi POST /api/v1/connections/requests/{id}/cancel", async () => {
    const postSpy = vi.spyOn(apiClient, "post").mockResolvedValue({
      success: true,
      data: undefined,
    });

    await connectionApi.cancelRequest("req-999");
    expect(postSpy).toHaveBeenCalledWith("/api/v1/connections/requests/req-999/cancel");
  });
});
