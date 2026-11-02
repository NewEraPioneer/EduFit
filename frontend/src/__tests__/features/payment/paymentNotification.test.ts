import { describe, it, expect } from "vitest";
import { getNotificationRoute } from "@/features/notification/components/NotificationDropdown";
describe("Payment notification routing", () => {
  it("routes each owner role to their wallet", () => {
    const notice = {
      notificationId: "n1",
      type: "PAYMENT_TOPUP",
      title: "Nạp thành công",
      shortText: "Đã cộng ví",
      isRead: false,
      createdAt: "2026-10-23",
    };
    expect(getNotificationRoute(notice, "STUDENT")).toBe("/student/payments");
    expect(getNotificationRoute(notice, "PARENT")).toBe("/parent/billing");
    expect(getNotificationRoute(notice, "TUTOR")).toBe("/tutor/earnings");
    expect(getNotificationRoute(notice, "ADMIN")).toBe("/admin/payments");
  });
});

it("routes session notifications to the shared case instead of a generic wallet", () => {
  const item = {
    notificationId: "notice",
    type: "PAYMENT_ADMIN_REVIEW",
    title: "Review",
    shortText: "Case",
    targetType: "PAYMENT_SESSION",
    targetId: "session-1",
    isRead: false,
    createdAt: "2026-10-26",
  };
  expect(getNotificationRoute(item, "PARENT")).toBe(
    "/parent/billing/sessions/session-1",
  );
  expect(getNotificationRoute(item, "ADMIN")).toBe("/admin/disputes/session-1");
});

it("keeps develop verification routes alongside payment notifications", () => {
  const notice = {
    notificationId: "verification-1",
    type: "QUALIFICATION_SUBMITTED",
    title: "Bằng cấp mới",
    shortText: "Cần thẩm định",
    isRead: false,
    createdAt: "2026-10-27",
  };
  expect(getNotificationRoute(notice, "ADMIN")).toBe(
    "/admin/verification-queue",
  );
  expect(getNotificationRoute(notice, "TUTOR")).toBe("/tutor/verification");
});
