import type { NotificationItem } from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";

export const NOTIFICATION_TARGET = {
  SESSION: "SESSION",
  CONNECTION_REQUEST: "CONNECTION_REQUEST",
  CLASS: "CLASS",
  PERIOD_REPORT: "PERIOD_REPORT",
  MILESTONE: "MILESTONE",
  LEARNING_PLAN: "LEARNING_PLAN",
  LINK_INVITATION: "LINK_INVITATION",
  PARENT_STUDENT_LINK: "PARENT_STUDENT_LINK",
  VERIFICATION: "VERIFICATION",
  PAYMENT_SESSION: "PAYMENT_SESSION",
  PAYMENT_WALLET: "PAYMENT_WALLET",
  REVIEW: "REVIEW",
} as const;

export type NotificationTargetType =
  (typeof NOTIFICATION_TARGET)[keyof typeof NOTIFICATION_TARGET];

export const DEEP_LINK_PARAM = {
  SESSION_ID: "sessionId",
  TAB: "tab",
  REQUEST_ID: "requestId",
  INVITATION_ID: "invitationId",
  ACTION: "action",
} as const;

interface RoleRoutes {
  tutor?: string;
  parent?: string;
  admin?: string;
  student: string;
}

/**
 * Chọn đường dẫn cơ sở dựa trên vai trò người dùng (DRY helper).
 */
function selectRoleRoute(role: string, routes: RoleRoutes): string {
  if (role === "ADMIN" && routes.admin) return routes.admin;
  if (role === "TUTOR" && routes.tutor) return routes.tutor;
  if (role === "PARENT" && routes.parent) return routes.parent;
  return routes.student;
}

/**
 * Đính kèm tham số query vào đường dẫn một cách an toàn và chuẩn xác (DRY helper).
 */
export function appendQueryParam(base: string, key: string, value?: string | null): string {
  if (!value) return base;
  const separator = base.includes("?") ? "&" : "?";
  return `${base}${separator}${key}=${encodeURIComponent(value)}`;
}

interface RouteRule {
  name: string;
  match: (type: string, targetType: string) => boolean;
  resolve: (item: NotificationItem, role: string) => string;
}

const ROUTE_RULES: RouteRule[] = [
  // 1. Phiên thanh toán (Payment Session)
  {
    name: "PAYMENT_SESSION",
    match: (_type, targetType) => targetType === NOTIFICATION_TARGET.PAYMENT_SESSION,
    resolve: (item, role) => {
      if (item.targetId) {
        const id = encodeURIComponent(item.targetId);
        if (role === "ADMIN") return `/admin/disputes/${id}`;
        const base = selectRoleRoute(role, {
          tutor: "/tutor/earnings",
          parent: "/parent/billing",
          student: "/student/payments",
        });
        return `${base}/sessions/${id}`;
      }
      return selectRoleRoute(role, {
        admin: "/admin/disputes",
        tutor: "/tutor/earnings",
        parent: "/parent/billing",
        student: "/student/payments",
      });
    },
  },

  // 2. Ví tiền / Biến động số dư chung (Payment Wallet)
  {
    name: "PAYMENT_WALLET",
    match: (type, targetType) =>
      type.startsWith("PAYMENT_") || targetType === NOTIFICATION_TARGET.PAYMENT_WALLET,
    resolve: (_item, role) =>
      selectRoleRoute(role, {
        tutor: "/tutor/earnings",
        parent: "/parent/billing",
        admin: "/admin/payments",
        student: "/student/payments",
      }),
  },

  // 3. Liên kết gia đình (Parent-Student Link & Invitations)
  {
    name: "FAMILY_LINK",
    match: (type, targetType) =>
      type.startsWith("LINK_") ||
      targetType === NOTIFICATION_TARGET.PARENT_STUDENT_LINK ||
      targetType === NOTIFICATION_TARGET.LINK_INVITATION,
    resolve: (item, role) => {
      if (role === "ADMIN") {
        return "/admin/family-links";
      }
      if (role === "TUTOR") {
        return "/tutor/dashboard";
      }

      const base = role === "PARENT" ? "/parent/children" : "/student/family";
      const isInvitation =
        item.targetType?.toUpperCase() === NOTIFICATION_TARGET.LINK_INVITATION ||
        item.type?.toUpperCase().includes("INVIT");
      return isInvitation
        ? appendQueryParam(base, DEEP_LINK_PARAM.INVITATION_ID, item.targetId)
        : base;
    },
  },

  // 4. Buổi học / Lịch học / Đổi lịch (Sessions & Scheduling)
  {
    name: "SESSION_AND_SCHEDULING",
    match: (type, targetType) =>
      type.startsWith("SESSION_") ||
      type.includes("RESCHEDULE") ||
      targetType === NOTIFICATION_TARGET.SESSION ||
      targetType === "TUTORING_SESSION" ||
      targetType === "RESCHEDULE_PROPOSAL",
    resolve: (item, role) => {
      // ADMIN không có trang lịch học, về dashboard quản trị
      if (role === "ADMIN") return "/admin/dashboard";

      const base = selectRoleRoute(role, {
        tutor: "/tutor/schedule",
        parent: "/parent/schedule",
        student: "/student/schedule",
      });

      // Đổi lịch hàng loạt trỏ về lớp: về trang lịch học không có sessionId
      if (item.targetType?.toUpperCase() === NOTIFICATION_TARGET.CLASS) {
        return base;
      }

      // Chỉ targetId của buổi học mới là sessionId; các targetType khác (vd. đề xuất đổi lịch) về trang lịch học
      const upperTarget = item.targetType?.toUpperCase() ?? "";
      if (upperTarget !== NOTIFICATION_TARGET.SESSION && upperTarget !== "TUTORING_SESSION") {
        return base;
      }

      // AC8: Gia sư nhận nhắc kết quả / phản hồi buổi học -> đính kèm action=outcome
      const upperType = item.type?.toUpperCase() || "";
      const isOutcomeAction =
        role === "TUTOR" &&
        (upperType === "SESSION_FEEDBACK_REMINDER" || upperType === "SESSION_OUTCOME_OVERDUE");

      let route = appendQueryParam(base, DEEP_LINK_PARAM.SESSION_ID, item.targetId);
      if (isOutcomeAction) {
        route = appendQueryParam(route, DEEP_LINK_PARAM.ACTION, "outcome");
      }
      return route;
    },
  },

  // 5. Yêu cầu kết nối / Mời học (Connection Requests)
  {
    name: "CONNECTION_REQUEST",
    match: (type, targetType) =>
      type.startsWith("CONNECTION_") || targetType === NOTIFICATION_TARGET.CONNECTION_REQUEST,
    resolve: (item, role) => {
      if (role === "ADMIN") return "/admin/dashboard";

      const upperType = item.type?.toUpperCase() || "";
      if (upperType === "CONNECTION_ACCEPTED") {
        return selectRoleRoute(role, {
          tutor: "/tutor/classes",
          parent: "/parent/children",
          student: "/student/classes",
        });
      }

      if (role === "TUTOR") {
        return appendQueryParam("/tutor/requests", DEEP_LINK_PARAM.REQUEST_ID, item.targetId);
      }
      if (role === "PARENT") {
        return "/parent/tutors";
      }

      return `/student/tutors?${DEEP_LINK_PARAM.TAB}=requests`;
    },
  },

  // 6. Lớp học (Classes)
  {
    name: "CLASS",
    match: (type, targetType) =>
      targetType === NOTIFICATION_TARGET.CLASS || type.startsWith("CLASS_"),
    resolve: (_item, role) => {
      if (role === "ADMIN") return "/admin/dashboard";
      return selectRoleRoute(role, {
        tutor: "/tutor/classes",
        parent: "/parent/children",
        student: "/student/classes",
      });
    },
  },

  // 7. Đánh giá (Reviews)
  {
    name: "REVIEW",
    match: (type, targetType) =>
      type.startsWith("REVIEW_") || targetType === NOTIFICATION_TARGET.REVIEW,
    resolve: (_item, role) => {
      if (role === "TUTOR") return "/tutor/reviews";
      if (role === "PARENT") return "/parent/dashboard";
      if (role === "ADMIN") return "/admin/dashboard";
      return "/student/classes";
    },
  },

  // 8. Cột mốc & Tiến độ học tập (Milestones & Progress)
  {
    name: "MILESTONES_AND_PROGRESS",
    match: (type, targetType) =>
      type.startsWith("MILESTONE_") ||
      type.startsWith("PROGRESS_") ||
      type.startsWith("PERIOD_REPORT") ||
      targetType === NOTIFICATION_TARGET.LEARNING_PLAN ||
      targetType === NOTIFICATION_TARGET.MILESTONE ||
      targetType === NOTIFICATION_TARGET.PERIOD_REPORT,
    resolve: (_item, role) => {
      if (role === "ADMIN") return "/admin/dashboard";

      return selectRoleRoute(role, {
        parent: "/parent/progress",
        tutor: "/tutor/classes",
        student: "/student/progress",
      });
    },
  },

  // 9. Xác thực hồ sơ KYC / thẩm định bằng cấp (Verification)
  {
    name: "VERIFICATION",
    match: (type, targetType) =>
      type.startsWith("KYC_") ||
      type.startsWith("VERIFICATION_") ||
      type.startsWith("QUALIFICATION_") ||
      targetType === NOTIFICATION_TARGET.VERIFICATION ||
      targetType === "KYC" ||
      targetType === "QUALIFICATION",
    resolve: (_item, role) => {
      if (role === "ADMIN") return "/admin/verification-queue";
      if (role === "TUTOR") return "/tutor/verification";
      return role === "PARENT" ? "/parent/dashboard" : "/student/dashboard";
    },
  },
];

/**
 * Trả về đường dẫn điều hướng chính xác cho từng loại thông báo và vai trò người dùng:
 * - Dựa trên bảng quy tắc (ROUTE_RULES).
 * - Tự động bổ sung tham số định danh (targetId) dưới dạng deep link query param.
 * - Đảm bảo vai trò ADMIN không rơi nhầm vào nhánh học sinh.
 */
export function getNotificationRoute(
  item: NotificationItem,
  role: UserRole = "STUDENT"
): string {
  const type = item.type?.toUpperCase() || "";
  const targetType = item.targetType?.toUpperCase() || "";
  const normalizedRole = role.toUpperCase();

  for (const rule of ROUTE_RULES) {
    if (rule.match(type, targetType)) {
      return rule.resolve(item, normalizedRole);
    }
  }

  // Mặc định: về Dashboard của vai trò tương ứng
  switch (normalizedRole) {
    case "TUTOR":
      return "/tutor/dashboard";
    case "PARENT":
      return "/parent/dashboard";
    case "ADMIN":
      return "/admin/dashboard";
    default:
      return "/student/dashboard";
  }
}
