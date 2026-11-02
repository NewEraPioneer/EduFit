import { describe, it, expect, vi, beforeEach } from "vitest";
import { progressApi } from "@/features/progress/api/progressApi";
import * as apiModule from "@/lib/api";

describe("Progress API Client", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("gọi GET /api/v1/progress/dashboard/student/{id} với các query params", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        studentId: "student-1",
        studentName: "Học sinh A",
        classes: [],
      },
    });

    const res = await progressApi.getStudentDashboard("student-1", "class-1", "60D");

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/progress/dashboard/student/student-1?range=60D&classId=class-1",
      { method: "GET" }
    );
    expect(res.data?.studentName).toBe("Học sinh A");
  });

  it("gọi GET /api/v1/progress/dashboard/parent để lấy dữ liệu con em", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { students: [] },
    });

    const res = await progressApi.getParentDashboard();

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/progress/dashboard/parent",
      { method: "GET" }
    );
    expect(res.data?.students).toEqual([]);
  });

  it("gọi GET /api/v1/progress/plans/{classId} để lấy kế hoạch học tập", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        planId: "plan-1",
        classId: "class-1",
        summary: "Kế hoạch Toán 9",
        version: 1,
        progressPercentage: 50,
        milestones: [],
        createdAt: "2026-10-17T00:00:00Z",
        updatedAt: "2026-10-17T00:00:00Z",
      },
    });

    const res = await progressApi.getPlanByClassId("class-1");

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/progress/plans/class-1",
      { method: "GET" }
    );
    expect(res.data?.summary).toBe("Kế hoạch Toán 9");
    expect(res.data?.progressPercentage).toBe(50);
  });

  it("gọi POST /api/v1/progress/plans để tạo kế hoạch học tập mới", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        planId: "plan-new",
        classId: "class-1",
        summary: "Kế hoạch mới",
        version: 1,
        milestones: [],
        createdAt: "2026-10-17T00:00:00Z",
        updatedAt: "2026-10-17T00:00:00Z",
      },
    });

    const payload = {
      classId: "class-1",
      summary: "Kế hoạch mới",
      milestones: [{ title: "Mốc 1", weight: 3 }],
    };

    const res = await progressApi.createPlan(payload);

    expect(fetchSpy).toHaveBeenCalledWith("/api/v1/progress/plans", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    expect(res.data?.planId).toBe("plan-new");
  });

  it("gọi PUT /api/v1/progress/plans/{planId} để cập nhật kế hoạch", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        planId: "plan-1",
        classId: "class-1",
        summary: "Kế hoạch sửa",
        version: 2,
        milestones: [],
        createdAt: "2026-10-17T00:00:00Z",
        updatedAt: "2026-10-17T00:00:00Z",
      },
    });

    const payload = {
      version: 1,
      summary: "Kế hoạch sửa",
      milestones: [{ title: "Mốc 1", weight: 3 }],
    };

    const res = await progressApi.updatePlan("plan-1", payload);

    expect(fetchSpy).toHaveBeenCalledWith("/api/v1/progress/plans/plan-1", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    expect(res.data?.version).toBe(2);
  });

  it("gọi PATCH /api/v1/progress/milestones/{id}/status để cập nhật trạng thái cột mốc", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        planId: "plan-1",
        classId: "class-1",
        version: 2,
        milestones: [],
        createdAt: "2026-10-17T00:00:00Z",
        updatedAt: "2026-10-17T00:00:00Z",
      },
    });

    const payload = {
      version: 1,
      status: "ACHIEVED" as const,
      achievedDate: "2026-10-17",
    };

    const res = await progressApi.updateMilestoneStatus("milestone-1", payload);

    expect(fetchSpy).toHaveBeenCalledWith("/api/v1/progress/milestones/milestone-1/status", {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    expect(res.data?.version).toBe(2);
  });

  it("gọi GET /api/v1/progress/classes/{classId}/notes để lấy timeline ghi chú", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
        first: true,
        last: true,
        empty: true,
      },
    });

    await progressApi.getClassNotes("class-1", { page: 0, size: 10 });

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/progress/classes/class-1/notes?page=0&size=10",
      { method: "GET" }
    );
  });

  it("gọi GET /api/v1/progress/teaching-history để lấy lịch sử dạy", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
        first: true,
        last: true,
        empty: true,
      },
    });

    await progressApi.getTeachingHistory({ page: 0, size: 10, status: "ACTIVE" });

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/progress/teaching-history?page=0&size=10&status=ACTIVE",
      { method: "GET" }
    );
  });

  it("gọi POST /api/v1/scheduling/sessions/{id}/outcome để ghi nhận biên bản buổi học", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { sessionId: "session-1", status: "COMPLETED" },
    });

    const payload = {
      outcome: "COMPLETED" as const,
      noteContent: "Học sinh hiểu bài tốt",
    };

    const res = await progressApi.recordSessionOutcome("session-1", payload);

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/sessions/session-1/outcome",
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
    expect(res.success).toBe(true);
  });
});
