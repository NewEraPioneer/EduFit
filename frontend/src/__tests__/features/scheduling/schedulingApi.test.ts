import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  schedulingApi,
  isConflictError,
  getConflictMessage,
} from "@/features/scheduling/api/schedulingApi";
import * as apiModule from "@/lib/api";
import { ApiError } from "@/lib/api";

describe("Scheduling API Client", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("gọi GET /api/v1/scheduling/calendar với đầy đủ query parameters", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: [
        {
          sessionId: "sess-1",
          classId: "cls-1",
          tutorId: "tut-1",
          studentId: "stu-1",
          startAt: "2026-10-18T08:00:00Z",
          endAt: "2026-10-18T09:30:00Z",
          mode: "ONLINE",
          placeOrLink: "https://meet.google.com/xyz",
          status: "SCHEDULED",
          isLateCancel: false,
        },
      ],
    });

    const res = await schedulingApi.getCalendar({
      from: "2026-10-18T00:00:00.000Z",
      to: "2026-10-24T23:59:59.999Z",
      studentId: "stu-1",
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/calendar?from=2026-10-18T00%3A00%3A00.000Z&to=2026-10-24T23%3A59%3A59.999Z&studentId=stu-1",
      { method: "GET" }
    );
    expect(res.data).toHaveLength(1);
    expect(res.data?.[0].status).toBe("SCHEDULED");
  });

  it("gọi GET /api/v1/scheduling/sessions/{id} để lấy chi tiết buổi học", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        sessionId: "sess-123",
        classId: "cls-1",
        tutorId: "tut-1",
        studentId: "stu-1",
        startAt: "2026-10-19T08:00:00Z",
        endAt: "2026-10-19T10:00:00Z",
        mode: "ONLINE",
        status: "PROPOSED",
        proposedBy: "user-1",
        expiresAt: "2026-10-21T08:00:00Z",
        isLateCancel: false,
        version: 0,
        createdAt: "2026-10-17T10:00:00Z",
      },
    });

    const res = await schedulingApi.getSessionDetail("sess-123");

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/sessions/sess-123",
      { method: "GET" }
    );
    expect(res.data?.sessionId).toBe("sess-123");
  });

  it("gọi POST /api/v1/scheduling/sessions/propose để tạo đề xuất buổi học", async () => {
    const payload = {
      classId: "b8f5223e-6b94-4dc4-b770-f4633b413a90",
      tutorId: "c2c54cf9-92c1-4ff2-bc48-69ce307a53c9",
      studentId: "d6f9a03c-e0bd-4fa6-843e-fcf6292b1a8d",
      startAt: "2026-10-20T08:00:00.000Z",
      endAt: "2026-10-20T10:00:00.000Z",
      mode: "ONLINE" as const,
      placeOrLink: "https://meet.google.com/test",
      message: "Buổi học thử",
    };

    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        sessionId: "new-sess-1",
        ...payload,
        status: "PROPOSED",
        proposedBy: payload.tutorId,
        expiresAt: "2026-10-22T08:00:00.000Z",
        isLateCancel: false,
        version: 0,
        createdAt: "2026-10-17T12:00:00.000Z",
      },
    });

    const res = await schedulingApi.proposeSession(payload);

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/sessions/propose",
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
    expect(res.data?.status).toBe("PROPOSED");
  });

  it("gọi POST /api/v1/scheduling/sessions/{id}/respond để chấp nhận hoặc từ chối đề xuất", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        sessionId: "sess-123",
        classId: "cls-1",
        tutorId: "tut-1",
        studentId: "stu-1",
        startAt: "2026-10-20T08:00:00.000Z",
        endAt: "2026-10-20T10:00:00.000Z",
        mode: "ONLINE",
        status: "SCHEDULED",
        proposedBy: "tut-1",
        expiresAt: "2026-10-22T08:00:00.000Z",
        isLateCancel: false,
        version: 1,
        createdAt: "2026-10-17T12:00:00.000Z",
      },
    });

    const res = await schedulingApi.respondToProposal("sess-123", {
      accept: true,
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/sessions/sess-123/respond",
      {
        method: "POST",
        body: JSON.stringify({ accept: true }),
      }
    );
    expect(res.data?.status).toBe("SCHEDULED");
  });

  it("gọi POST /api/v1/scheduling/sessions/{id}/reschedule để yêu cầu dời lịch học", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        proposalId: "prop-1",
        sessionId: "sess-123",
        proposedBy: "stu-1",
        newStartAt: "2026-10-21T09:00:00.000Z",
        newEndAt: "2026-10-21T11:00:00.000Z",
        reason: "Bận thi học kỳ",
        status: "PENDING",
        expiresAt: "2026-10-23T09:00:00.000Z",
        createdAt: "2026-10-17T12:00:00.000Z",
      },
    });

    const payload = {
      newStartAt: "2026-10-21T09:00:00.000Z",
      newEndAt: "2026-10-21T11:00:00.000Z",
      reason: "Bận thi học kỳ",
    };

    const res = await schedulingApi.rescheduleSession("sess-123", payload);

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/sessions/sess-123/reschedule",
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
    expect(res.data?.status).toBe("PENDING");
  });

  it("gọi POST /api/v1/scheduling/sessions/{id}/cancel để hủy buổi học", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        sessionId: "sess-123",
        classId: "cls-1",
        tutorId: "tut-1",
        studentId: "stu-1",
        startAt: "2026-10-20T08:00:00.000Z",
        endAt: "2026-10-20T10:00:00.000Z",
        mode: "ONLINE",
        status: "CANCELLED",
        cancelReason: "SCHEDULE_CONFLICT",
        cancelComment: "Trùng ca trực đột xuất",
        isLateCancel: false,
        proposedBy: "tut-1",
        expiresAt: "2026-10-22T08:00:00.000Z",
        version: 2,
        createdAt: "2026-10-17T12:00:00.000Z",
      },
    });

    const payload = {
      reason: "SCHEDULE_CONFLICT" as const,
      comment: "Trùng ca trực đột xuất",
    };

    const res = await schedulingApi.cancelSession("sess-123", payload);

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/scheduling/sessions/sess-123/cancel",
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
    expect(res.data?.status).toBe("CANCELLED");
  });

  describe("Recurring Schedule Endpoints (UC4.8 Bounded Horizon)", () => {
    it("gọi POST /api/v1/scheduling/recurring/propose để tạo đề xuất lịch định kỳ", async () => {
      const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
        success: true,
        data: {
          proposalId: "prop-123",
          classId: "cls-1",
          tutorId: "tut-1",
          studentId: "stu-1",
          proposedBy: "user-1",
          effectiveFrom: "2026-10-19",
          effectiveTo: "2026-11-16",
          newWeeklySlots: ["MONDAY 18:00-20:00", "WEDNESDAY 18:00-20:00"],
          defaultMode: "ONLINE",
          placeOrLink: "https://meet.google.com/xyz",
          message: "Lịch học 4 tuần tới",
          status: "PENDING",
          expiresAt: "2026-10-21T00:00:00Z",
          createdAt: "2026-10-19T00:00:00Z",
        },
      });

      const payload = {
        classId: "cls-1",
        effectiveFrom: "2026-10-19",
        effectiveTo: "2026-11-16",
        newWeeklySlots: ["MONDAY 18:00-20:00", "WEDNESDAY 18:00-20:00"],
        mode: "ONLINE" as const,
        placeOrLink: "https://meet.google.com/xyz",
        message: "Lịch học 4 tuần tới",
      };

      const res = await schedulingApi.proposeRecurringSchedule(payload);

      expect(fetchSpy).toHaveBeenCalledWith("/api/v1/scheduling/recurring/propose", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      expect(res.data?.proposalId).toBe("prop-123");
      expect(res.data?.status).toBe("PENDING");
      expect(res.data?.newWeeklySlots).toHaveLength(2);
    });

    it("gọi POST /api/v1/scheduling/recurring/respond để phản hồi đề xuất lịch định kỳ", async () => {
      const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
        success: true,
        data: {
          proposal: {
            proposalId: "prop-123",
            classId: "cls-1",
            status: "ACCEPTED",
          },
          cancelledSessionCount: 2,
          generatedSessions: [],
        },
      });

      const payload = {
        proposalId: "prop-123",
        accept: true,
      };

      const res = await schedulingApi.respondRecurringSchedule(payload);

      expect(fetchSpy).toHaveBeenCalledWith("/api/v1/scheduling/recurring/respond", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      expect(res.data?.proposal.status).toBe("ACCEPTED");
      expect(res.data?.cancelledSessionCount).toBe(2);
    });

    it("gọi GET /api/v1/scheduling/recurring/classes/{classId}/active-proposal", async () => {
      const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
        success: true,
        data: {
          proposalId: "prop-123",
          classId: "cls-1",
          status: "PENDING",
        },
      });

      const res = await schedulingApi.getActiveRecurringProposal("cls-1");

      expect(fetchSpy).toHaveBeenCalledWith(
        "/api/v1/scheduling/recurring/classes/cls-1/active-proposal",
        { method: "GET" }
      );
      expect(res.data?.proposalId).toBe("prop-123");
    });

    it("gọi GET /api/v1/scheduling/recurring/proposals/{proposalId}", async () => {
      const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
        success: true,
        data: {
          proposalId: "prop-123",
          classId: "cls-1",
          status: "PENDING",
        },
      });

      const res = await schedulingApi.getRecurringProposal("prop-123");

      expect(fetchSpy).toHaveBeenCalledWith(
        "/api/v1/scheduling/recurring/proposals/prop-123",
        { method: "GET" }
      );
      expect(res.data?.proposalId).toBe("prop-123");
    });
  });


  describe("Phát hiện lỗi xung đột 409 PostgreSQL GiST (isConflictError)", () => {
    it("nhận diện đúng ApiError với status 409", () => {
      const conflictError = new ApiError("Trùng lịch", 409, "SCHEDULE_OVERLAP");
      expect(isConflictError(conflictError)).toBe(true);
    });

    it("nhận diện đúng lỗi với mã SCHEDULE_OVERLAP", () => {
      const overlapError = new ApiError("Conflict", 400, "SCHEDULE_OVERLAP");
      expect(isConflictError(overlapError)).toBe(true);
    });

    it("nhận diện đúng lỗi với mã CONFLICT_DETECTED", () => {
      const overlapError = new ApiError("Conflict", 400, "CONFLICT_DETECTED");
      expect(isConflictError(overlapError)).toBe(true);
    });

    it("nhận diện đúng object lỗi thông thường có status 409", () => {
      const rawError = { status: 409, message: "Lịch bị trùng" };
      expect(isConflictError(rawError)).toBe(true);
    });

    it("trả về false đối với các lỗi khác (400, 404, 500)", () => {
      expect(isConflictError(new ApiError("Not found", 404))).toBe(false);
      expect(isConflictError(new ApiError("Bad request", 400, "VALIDATION_FAILED"))).toBe(false);
      expect(isConflictError(new Error("Network down"))).toBe(false);
    });

    it("trả về message thông điệp thân thiện với getConflictMessage", () => {
      const err = new ApiError("Gia sư đã có lịch dạy khác trong khoảng thời gian này!", 409);
      expect(getConflictMessage(err)).toBe("Gia sư đã có lịch dạy khác trong khoảng thời gian này!");

      const fallback = getConflictMessage(new Error());
      expect(fallback).toBe("Khung giờ này đã bị trùng với lịch khác! Vui lòng chọn một khung giờ khác.");
    });
  });
});
