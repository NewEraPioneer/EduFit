import type { NotificationItem } from "@/features/notification";

export const NOTIFICATION_TEST_IDS = {
  SESSION_1: "session-test-uuid-001",
  SESSION_2: "session-test-uuid-002",
  NOTIFICATION_1: "notif-test-uuid-001",
  NOTIFICATION_2: "notif-test-uuid-002",
  CONNECTION_REQUEST_1: "conn-req-test-uuid-001",
  CLASS_1: "class-test-uuid-001",
  REPORT_1: "report-test-uuid-001",
  MILESTONE_1: "milestone-test-uuid-001",
  LINK_1: "link-test-uuid-001",
  INVITATION_1: "invitation-test-uuid-001",
  REVIEW_1: "review-test-uuid-001",
  VERIFICATION_1: "verification-test-uuid-001",
} as const;

export const NOTIFICATION_TEST_TIMEOUTS = {
  POLL_INTERVAL_MS: 15_000,
  AUTO_DISMISS_MS: 4_500,
  FAST_ADVANCE_MS: 100,
} as const;

export function buildNotificationFixture(
  overrides: Partial<NotificationItem> = {}
): NotificationItem {
  return {
    notificationId: NOTIFICATION_TEST_IDS.NOTIFICATION_1,
    type: "SESSION_PROPOSED",
    title: "Đề xuất buổi học",
    shortText: "Buổi học mới cần xác nhận",
    targetType: "SESSION",
    targetId: NOTIFICATION_TEST_IDS.SESSION_1,
    isRead: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

