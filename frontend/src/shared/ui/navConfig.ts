import {
  AlertTriangle,
  BarChart3,
  BookOpen,
  Calendar,
  CreditCard,
  DollarSign,
  FileCheck2,
  GraduationCap,
  HeartHandshake,
  LayoutDashboard,
  type LucideIcon,
  Search,
  Settings,
  ShieldCheck,
  Star,
  TrendingUp,
  UserCheck,
  Users,
} from "lucide-react";

export type UserRole = "STUDENT" | "TUTOR" | "PARENT" | "ADMIN";

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  badge?: string | null;
}

export interface NavSection {
  title: string;
  items: NavItem[];
}

interface RoleNav {
  roleTitle: string;
  sections: NavSection[];
  /** Tối đa 5 mục cho thanh dưới trên mobile. Các mục còn lại nằm trong menu. */
  bottom: NavItem[];
}

const item = (label: string, href: string, icon: LucideIcon, badge?: string): NavItem => ({ label, href, icon, badge });

const SETTINGS = (role: string) => item("Cài đặt", `/${role}/settings`, Settings);

const studentHome = item("Trang chủ", "/student/dashboard", LayoutDashboard);
const studentSchedule = item("Lịch học", "/student/schedule", Calendar);
const studentTutors = item("Tìm gia sư", "/student/tutors", Search);
const studentProgress = item("Tiến độ học tập", "/student/progress", TrendingUp);
const studentPayments = item("Ví & Học phí", "/student/payments", CreditCard);

const tutorHome = item("Trang chủ", "/tutor/dashboard", LayoutDashboard);
const tutorSchedule = item("Lịch dạy", "/tutor/schedule", Calendar);
const tutorClasses = item("Lớp học", "/tutor/classes", BookOpen);
const tutorRequests = item("Yêu cầu học viên", "/tutor/requests", UserCheck);
const tutorEarnings = item("Thu nhập & Rút tiền", "/tutor/earnings", DollarSign);

const parentHome = item("Trang chủ", "/parent/dashboard", LayoutDashboard);
const parentProgress = item("Tiến độ học tập", "/parent/progress", BarChart3);
const parentSchedule = item("Lịch học gia đình", "/parent/schedule", Calendar);
const parentTutors = item("Tìm gia sư", "/parent/tutors", Search);
const parentBilling = item("Ví & Học phí", "/parent/billing", CreditCard);

const adminHome = item("Tổng quan", "/admin/dashboard", LayoutDashboard);
const adminKyc = item("Duyệt bằng cấp KYC", "/admin/verification-queue", ShieldCheck);
const adminPayments = item("Đối soát thanh toán", "/admin/payments", CreditCard);
const adminDisputes = item("Khiếu nại & Tranh chấp", "/admin/disputes", AlertTriangle);

export const ROLE_NAV: Record<UserRole, RoleNav> = {
  STUDENT: {
    roleTitle: "Học sinh",
    sections: [
      { title: "Học tập", items: [studentHome, studentSchedule, item("Lớp của tôi", "/student/classes", BookOpen), studentProgress, studentTutors] },
      { title: "Tài khoản", items: [item("Liên kết phụ huynh", "/student/family", HeartHandshake), studentPayments, SETTINGS("student")] },
    ],
    bottom: [studentHome, studentSchedule, studentTutors, studentProgress, studentPayments],
  },
  TUTOR: {
    roleTitle: "Gia sư",
    sections: [
      {
        title: "Giảng dạy",
        items: [tutorHome, tutorSchedule, tutorClasses, tutorRequests, item("Lịch sử giảng dạy", "/tutor/history", GraduationCap), item("Đánh giá", "/tutor/reviews", Star)],
      },
      { title: "Tài khoản", items: [item("Xác minh bằng cấp", "/tutor/verification", FileCheck2, "KYC"), tutorEarnings, SETTINGS("tutor")] },
    ],
    bottom: [tutorHome, tutorSchedule, tutorClasses, tutorRequests, tutorEarnings],
  },
  PARENT: {
    roleTitle: "Phụ huynh",
    sections: [
      { title: "Theo dõi", items: [parentHome, parentProgress, parentSchedule, parentTutors] },
      { title: "Tài khoản", items: [item("Liên kết con em", "/parent/children", HeartHandshake), parentBilling, SETTINGS("parent")] },
    ],
    bottom: [parentHome, parentSchedule, parentProgress, parentTutors, parentBilling],
  },
  ADMIN: {
    roleTitle: "Quản trị",
    sections: [
      { title: "Hệ thống", items: [adminHome, adminKyc, adminPayments] },
      {
        title: "Dữ liệu",
        items: [
          item("Học sinh & Phụ huynh", "/admin/learners", Users),
          item("Gia sư", "/admin/tutors-management", GraduationCap),
          item("Liên kết Phụ huynh - Con", "/admin/family-links", HeartHandshake),
          item("Danh mục môn học", "/admin/catalogs", BookOpen),
          adminDisputes,
        ],
      },
      { title: "Cấu hình", items: [item("Cài đặt hệ thống", "/admin/settings", Settings)] },
    ],
    bottom: [adminHome, adminKyc, adminPayments, adminDisputes],
  },
};

/** Nhóm thông báo làm hiện chấm đỏ trên từng mục menu. */
export const NAV_NOTIFICATION_CATEGORY: Record<string, string> = {
  "/student/family": "LINK",
  "/student/progress": "PROGRESS",
  "/student/tutors": "CONNECTION",
  "/student/classes": "CONNECTION",
  "/student/schedule": "SCHEDULE",
  "/student/payments": "PAYMENT",
  "/tutor/verification": "VERIFICATION",
  "/tutor/schedule": "SCHEDULE",
  "/tutor/classes": "CONNECTION",
  "/tutor/requests": "CONNECTION",
  "/tutor/reviews": "REVIEW",
  "/tutor/earnings": "PAYMENT",
  "/parent/children": "LINK",
  "/parent/progress": "PROGRESS",
  "/parent/schedule": "SCHEDULE",
  "/parent/billing": "PAYMENT",
  "/admin/verification-queue": "VERIFICATION",
  "/admin/payments": "PAYMENT",
};

export function isNavActive(href: string, pathname: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** Tên trang cho thanh trên cùng, lấy từ chính mục menu đang mở. */
export function pageTitle(role: UserRole, pathname: string | null) {
  const items = ROLE_NAV[role].sections.flatMap((section) => section.items);
  return items.find((entry) => pathname && isNavActive(entry.href, pathname))?.label ?? ROLE_NAV[role].roleTitle;
}
