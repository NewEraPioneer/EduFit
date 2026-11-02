export type AccountRole = "STUDENT" | "TUTOR" | "PARENT" | "ADMIN";
export type AccountStatus = "ACTIVE" | "DEACTIVATED";

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  role: AccountRole;
  status: AccountStatus;
  avatarUrl?: string | null;
  createdAt: string;
  lastLoginAt?: string | null;
}

export interface AdminUserListResponse {
  users: AdminUser[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface AdminUserStats {
  totalUsers: number;
  totalStudents: number;
  totalTutors: number;
  totalParents: number;
  totalActive: number;
  totalDeactivated: number;
  activeStudents?: number;
  deactivatedStudents?: number;
  activeParents?: number;
  deactivatedParents?: number;
  activeTutors?: number;
  deactivatedTutors?: number;
}

export interface GetUsersParams {
  role?: string;
  status?: string;
  search?: string;
  page?: number;
  size?: number;
}

export type FamilyLinkStatus = "ALL" | "CONFIRMED" | "REVOKED";

export interface AdminParentStudentLink {
  linkId: string;
  parentUserId: string;
  parentFullName: string;
  parentEmail: string;
  studentId: string;
  studentUserId: string;
  studentFullName: string;
  studentEmail: string;
  status: "CONFIRMED" | "REVOKED";
  confirmedAt: string;
  revokedAt?: string | null;
  revokeReason?: string | null;
}

export interface AdminParentStudentLinkListResponse {
  items: AdminParentStudentLink[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface AdminParentStudentLinkStats {
  totalLinks: number;
  activeLinks: number;
  revokedLinks: number;
  uniqueParents: number;
  uniqueStudents: number;
}

export interface GetFamilyLinksParams {
  status?: string;
  search?: string;
  page?: number;
  size?: number;
}

export interface AdminDashboardStats {
  totalUsers: number;
  totalTutors: number;
  totalStudents: number;
  totalParents: number;
  activeClasses: number;
  completedSessions: number;
  pendingVerifications?: number;
  pendingKyc?: number;
}

export interface SubjectAdmin {
  id: number;
  name: string;
  code?: string;
  slug?: string;
  category?: string;
  description?: string;
  isActive: boolean;
  createdAt?: string;
}

export interface CreateSubjectPayload {
  name: string;
  category?: string;
  code?: string;
  slug?: string;
  description?: string;
}

export interface UpdateSubjectStatusPayload {
  isActive: boolean;
}

export interface AuditLogItem {
  id: string;
  actorUserId?: string;
  actor?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  reason?: string;
  details?: string;
  ipAddress?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface PagedAuditLogs {
  items: AuditLogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditLogQueryParams {
  page?: number;
  size?: number;
  action?: string;
}

export interface ParentStudentAdminLink {
  id?: string;
  linkId?: string;
  parentId?: string;
  parentEmail?: string;
  parentName?: string;
  studentId?: string;
  studentEmail?: string;
  studentName?: string;
  status: string;
  createdAt?: string;
}
