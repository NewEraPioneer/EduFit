import { describe, it, expect } from "vitest";
import {
  getNotificationRoute,
  NOTIFICATION_TARGET,
  DEEP_LINK_PARAM,
} from "@/features/notification/lib/notificationRoute";
import {
  NOTIFICATION_TEST_IDS,
  buildNotificationFixture,
} from "@/__tests__/fixtures/notification-test-fixtures";
import type { UserRole } from "@/shared/ui/Sidebar";

describe("notificationRoute - Hằng số và quy tắc điều hướng", () => {
  it("xuất đầy đủ các hằng số NOTIFICATION_TARGET", () => {
    expect(NOTIFICATION_TARGET.SESSION).toBe("SESSION");
    expect(NOTIFICATION_TARGET.CONNECTION_REQUEST).toBe("CONNECTION_REQUEST");
    expect(NOTIFICATION_TARGET.CLASS).toBe("CLASS");
    expect(NOTIFICATION_TARGET.PERIOD_REPORT).toBe("PERIOD_REPORT");
    expect(NOTIFICATION_TARGET.MILESTONE).toBe("MILESTONE");
    expect(NOTIFICATION_TARGET.LINK_INVITATION).toBe("LINK_INVITATION");
    expect(NOTIFICATION_TARGET.PARENT_STUDENT_LINK).toBe("PARENT_STUDENT_LINK");
    expect(NOTIFICATION_TARGET.VERIFICATION).toBe("VERIFICATION");
    expect(NOTIFICATION_TARGET.PAYMENT_SESSION).toBe("PAYMENT_SESSION");
    expect(NOTIFICATION_TARGET.PAYMENT_WALLET).toBe("PAYMENT_WALLET");
    expect(NOTIFICATION_TARGET.REVIEW).toBe("REVIEW");
  });

  it("C3: xuất đúng và tinh gọn các hằng số DEEP_LINK_PARAM thực tế được sử dụng", () => {
    expect(DEEP_LINK_PARAM.SESSION_ID).toBe("sessionId");
    expect(DEEP_LINK_PARAM.TAB).toBe("tab");
    expect(DEEP_LINK_PARAM.REQUEST_ID).toBe("requestId");
    expect(DEEP_LINK_PARAM.INVITATION_ID).toBe("invitationId");
    expect(DEEP_LINK_PARAM.ACTION).toBe("action");
  });

  describe("AC6 & AC8: Điều hướng buổi học (SESSION)", () => {
    const sessionTypes = [
      "SESSION_PROPOSED",
      "SESSION_PROPOSAL_REJECTED",
      "SESSION_SCHEDULED",
      "SESSION_RESCHEDULED",
      "SESSION_RESCHEDULE_PROPOSED",
      "SESSION_RESCHEDULE_REJECTED",
      "SESSION_CANCELLED",
      "SESSION_EXPIRED",
      "SESSION_REMINDER",
    ];

    for (const type of sessionTypes) {
      it(`đưa học sinh tới /student/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1} cho ${type}`, () => {
        const item = buildNotificationFixture({
          type,
          targetType: NOTIFICATION_TARGET.SESSION,
          targetId: NOTIFICATION_TEST_IDS.SESSION_1,
        });
        expect(getNotificationRoute(item, "STUDENT")).toBe(
          `/student/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}`
        );
      });

      it(`đưa phụ huynh tới /parent/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1} cho ${type}`, () => {
        const item = buildNotificationFixture({
          type,
          targetType: NOTIFICATION_TARGET.SESSION,
          targetId: NOTIFICATION_TEST_IDS.SESSION_1,
        });
        expect(getNotificationRoute(item, "PARENT")).toBe(
          `/parent/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}`
        );
      });

      it(`đưa gia sư tới /tutor/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1} cho ${type}`, () => {
        const item = buildNotificationFixture({
          type,
          targetType: NOTIFICATION_TARGET.SESSION,
          targetId: NOTIFICATION_TEST_IDS.SESSION_1,
        });
        expect(getNotificationRoute(item, "TUTOR")).toBe(
          `/tutor/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}`
        );
      });

      it(`ADMIN KHÔNG rơi vào nhánh student mà về /admin/dashboard cho ${type}`, () => {
        const item = buildNotificationFixture({
          type,
          targetType: NOTIFICATION_TARGET.SESSION,
          targetId: NOTIFICATION_TEST_IDS.SESSION_1,
        });
        expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
      });
    }

    it("AC8: Gia sư nhận SESSION_FEEDBACK_REMINDER mang action=outcome", () => {
      const item = buildNotificationFixture({
        type: "SESSION_FEEDBACK_REMINDER",
        targetType: NOTIFICATION_TARGET.SESSION,
        targetId: NOTIFICATION_TEST_IDS.SESSION_1,
      });
      expect(getNotificationRoute(item, "TUTOR")).toBe(
        `/tutor/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`
      );
    });

    it("AC8: Gia sư nhận SESSION_OUTCOME_OVERDUE mang action=outcome", () => {
      const item = buildNotificationFixture({
        type: "SESSION_OUTCOME_OVERDUE",
        targetType: NOTIFICATION_TARGET.SESSION,
        targetId: NOTIFICATION_TEST_IDS.SESSION_1,
      });
      expect(getNotificationRoute(item, "TUTOR")).toBe(
        `/tutor/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`
      );
    });
  });

  it("targetType không phải buổi học (RESCHEDULE_PROPOSAL) không gắn sessionId sai", () => {
    const item = buildNotificationFixture({
      type: "RESCHEDULE_PROPOSED",
      targetType: "RESCHEDULE_PROPOSAL",
      targetId: NOTIFICATION_TEST_IDS.SESSION_1,
    });
    expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/schedule");
    expect(getNotificationRoute(item, "STUDENT")).toBe("/student/schedule");
  });

  it.each(["SESSION_PROPOSAL", "SESSION_UPDATE"])(
    "targetType cũ %s vẫn trỏ thẳng tới buổi học",
    (targetType) => {
      const item = buildNotificationFixture({
        type: "SESSION_UPDATE",
        targetType,
        targetId: NOTIFICATION_TEST_IDS.SESSION_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe(
        `/student/schedule?sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}`,
      );
    },
  );

  describe("AC6: Thay đổi lịch hàng loạt (CLASS schedule changes)", () => {
    const bulkTypes = [
      "SESSION_RECURRING_CHANGED",
      "SESSION_BULK_CANCELLED",
      "SESSION_BATCH_SCHEDULED",
      "SESSION_BATCH_REJECTED",
    ];

    for (const type of bulkTypes) {
      it(`đưa đúng về trang lịch học cho ${type}`, () => {
        const item = buildNotificationFixture({
          type,
          targetType: NOTIFICATION_TARGET.CLASS,
          targetId: NOTIFICATION_TEST_IDS.CLASS_1,
        });
        expect(getNotificationRoute(item, "STUDENT")).toBe("/student/schedule");
        expect(getNotificationRoute(item, "PARENT")).toBe("/parent/schedule");
        expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/schedule");
        expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
      });
    }
  });

  describe("AC6: Yêu cầu kết nối và lớp học", () => {
    it("CONNECTION_REQUESTED mang tab=requests cho học sinh và requestId cho gia sư", () => {
      const item = buildNotificationFixture({
        type: "CONNECTION_REQUESTED",
        targetType: NOTIFICATION_TARGET.CONNECTION_REQUEST,
        targetId: NOTIFICATION_TEST_IDS.CONNECTION_REQUEST_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe(
        "/student/tutors?tab=requests"
      );
      expect(getNotificationRoute(item, "TUTOR")).toBe(
        `/tutor/requests?requestId=${NOTIFICATION_TEST_IDS.CONNECTION_REQUEST_1}`
      );
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/tutors");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
    });

    it("CONNECTION_ACCEPTED với targetType CLASS không mang classId thừa", () => {
      const item = buildNotificationFixture({
        type: "CONNECTION_ACCEPTED",
        targetType: NOTIFICATION_TARGET.CLASS,
        targetId: NOTIFICATION_TEST_IDS.CLASS_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/classes");
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/classes");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/children");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
    });
  });

  describe("AC6: Tiến độ và Cột mốc (PROGRESS & MILESTONE)", () => {
    it("MILESTONE_ACHIEVED điều hướng không mang milestoneId thừa", () => {
      const item = buildNotificationFixture({
        type: "MILESTONE_ACHIEVED",
        targetType: NOTIFICATION_TARGET.MILESTONE,
        targetId: NOTIFICATION_TEST_IDS.MILESTONE_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/progress");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/progress");
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/classes");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
    });

    it("PERIOD_REPORT_SENT điều hướng không mang reportId thừa", () => {
      const item = buildNotificationFixture({
        type: "PERIOD_REPORT_SENT",
        targetType: NOTIFICATION_TARGET.PERIOD_REPORT,
        targetId: NOTIFICATION_TEST_IDS.REPORT_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/progress");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/progress");
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/classes");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
    });
  });

  describe("AC6: Lời mời và liên kết gia đình (LINK)", () => {
    it("LINK_INVITED mang invitationId", () => {
      const item = buildNotificationFixture({
        type: "LINK_INVITED",
        targetType: NOTIFICATION_TARGET.LINK_INVITATION,
        targetId: NOTIFICATION_TEST_IDS.INVITATION_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe(
        `/student/family?invitationId=${NOTIFICATION_TEST_IDS.INVITATION_1}`
      );
      expect(getNotificationRoute(item, "PARENT")).toBe(
        `/parent/children?invitationId=${NOTIFICATION_TEST_IDS.INVITATION_1}`
      );
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/family-links");
    });

    it("LINK_CONFIRMED với PARENT_STUDENT_LINK không mang linkId thừa", () => {
      const item = buildNotificationFixture({
        type: "LINK_CONFIRMED",
        targetType: NOTIFICATION_TARGET.PARENT_STUDENT_LINK,
        targetId: NOTIFICATION_TEST_IDS.LINK_1,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/family");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/children");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/family-links");
    });
  });

  describe("AC6: Xác thực gia sư (VERIFICATION)", () => {
    it("VERIFICATION_APPROVED không mang requestId thừa", () => {
      const item = buildNotificationFixture({
        type: "VERIFICATION_APPROVED",
        targetType: NOTIFICATION_TARGET.VERIFICATION,
        targetId: NOTIFICATION_TEST_IDS.VERIFICATION_1,
      });
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/verification-queue");
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/verification");
    });
  });

  describe("AC6: Đánh giá (REVIEW)", () => {
    it("REVIEW_RECEIVED điều hướng không mang reviewId thừa", () => {
      const item = buildNotificationFixture({
        type: "REVIEW_RECEIVED",
        targetType: NOTIFICATION_TARGET.REVIEW,
        targetId: NOTIFICATION_TEST_IDS.REVIEW_1,
      });
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/reviews");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/dashboard");
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/classes");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/dashboard");
    });
  });

  describe("AC6: Thanh toán (PAYMENT)", () => {
    it("PAYMENT_SESSION điều hướng tới chi tiết session theo id", () => {
      const item = buildNotificationFixture({
        type: "PAYMENT_SETTLED",
        targetType: NOTIFICATION_TARGET.PAYMENT_SESSION,
        targetId: "sess-abc",
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/payments/sessions/sess-abc");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/billing/sessions/sess-abc");
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/earnings/sessions/sess-abc");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/disputes/sess-abc");
    });

    it("PAYMENT_WALLET điều hướng tới trang ví của vai trò", () => {
      const item = buildNotificationFixture({
        type: "PAYMENT_TOPUP",
        targetType: NOTIFICATION_TARGET.PAYMENT_WALLET,
      });
      expect(getNotificationRoute(item, "STUDENT")).toBe("/student/payments");
      expect(getNotificationRoute(item, "PARENT")).toBe("/parent/billing");
      expect(getNotificationRoute(item, "TUTOR")).toBe("/tutor/earnings");
      expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/payments");
    });
  });
});
