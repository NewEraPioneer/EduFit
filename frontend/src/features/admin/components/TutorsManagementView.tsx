"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import {
  GraduationCap,
  Search,
  RefreshCw,
  Lock,
  Unlock,
  AlertCircle,
  CheckCircle2,
  Filter,
  Loader2,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";
import { adminUserApi } from "../api/adminUserApi";
import type { AdminUser, AdminUserStats, AccountStatus } from "../types";
import { UserStatusBadge } from "./UserStatusBadge";
import { UserRoleBadge } from "./UserRoleBadge";
import { UserActionModal } from "./UserActionModal";
import { EmptyState } from "@/shared/ui/EmptyState";

export function TutorsManagementView() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [stats, setStats] = useState<AdminUserStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  // Filters
  const [selectedStatus, setSelectedStatus] = useState<"ALL" | AccountStatus>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Modal action
  const [actionUser, setActionUser] = useState<AdminUser | null>(null);
  const [actionType, setActionType] = useState<"lock" | "unlock" | null>(null);

  const fetchStats = useCallback(async () => {
    try {
      const res = await adminUserApi.getUserStats();
      if (res.success && res.data) {
        setStats(res.data);
      }
    } catch (e) {
      console.error("Lỗi khi tải thống kê:", e);
    }
  }, []);

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const statusParam = selectedStatus === "ALL" ? undefined : selectedStatus;

      const res = await adminUserApi.getUsers({
        role: "TUTOR",
        status: statusParam,
        search: searchQuery.trim() || undefined,
        page,
        size: 15,
      });

      if (res.success && res.data) {
        setUsers(res.data.users);
        setTotalPages(res.data.totalPages);
        setTotalElements(res.data.totalElements);
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Không thể tải danh sách gia sư");
      }
    } finally {
      setLoading(false);
    }
  }, [selectedStatus, searchQuery, page]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleRefresh = () => {
    fetchStats();
    fetchUsers();
  };

  const handleOpenLock = (user: AdminUser) => {
    setActionUser(user);
    setActionType("lock");
  };

  const handleOpenUnlock = (user: AdminUser) => {
    setActionUser(user);
    setActionType("unlock");
  };

  const handleCloseModal = () => {
    setActionUser(null);
    setActionType(null);
  };

  const handleConfirmAction = async (user: AdminUser, reason?: string) => {
    if (actionType === "lock") {
      const res = await adminUserApi.lockUser(user.id, reason);
      if (!res.success) {
        throw new Error(res.message || "Không thể khóa tài khoản gia sư");
      }
      setSuccessToast(`Đã khóa tài khoản gia sư "${user.fullName}" thành công`);
    } else if (actionType === "unlock") {
      const res = await adminUserApi.unlockUser(user.id);
      if (!res.success) {
        throw new Error(res.message || "Không thể mở khóa tài khoản gia sư");
      }
      setSuccessToast(`Đã mở khóa tài khoản gia sư "${user.fullName}" thành công`);
    }

    setTimeout(() => setSuccessToast(null), 4000);
    fetchStats();
    fetchUsers();
  };

  const formatDate = (dateString?: string | null) => {
    if (!dateString) return "—";
    try {
      const d = new Date(dateString);
      return d.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      });
    } catch {
      return dateString;
    }
  };

  return (
    <div className="space-y-6">
      {/* Toast Feedback */}
      {successToast && (
        <div className="p-4 bg-success-soft border border-success-line rounded-2xl text-success-strong text-xs font-medium flex items-center justify-between gap-3 animate-in slide-in-from-top duration-200">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="size-4 text-success-strong shrink-0" />
            <span>{successToast}</span>
          </div>
          <button
            type="button"
            onClick={() => setSuccessToast(null)}
            className="text-success-strong hover:text-success-strong text-xs font-medium"
          >
            Đóng
          </button>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-surface p-6 rounded-3xl border border-line/90 shadow-xs">
        <div>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-soft text-brand-strong rounded-full text-xs font-medium mb-2">
            <GraduationCap className="size-3.5 text-brand-strong" />
            <span>Quản Trị Gia Sư</span>
          </div>
          <h2 className="font-heading text-xl sm:text-2xl font-bold text-ink">
            Danh Sách Gia Sư
          </h2>
          <p className="text-xs text-muted mt-1">
            Quản lý hồ sơ, tình trạng hoạt động và kiểm soát phân quyền gia sư giảng dạy trên EduFit
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <Link
            href="/admin/verification-queue"
            className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-brand-soft hover:bg-brand-soft text-brand-strong rounded-xl text-xs font-medium transition-colors border border-brand-line"
          >
            <ShieldCheck className="size-3.5 text-brand-strong" />
            <span>Duyệt bằng cấp KYC</span>
          </Link>
          <button
            type="button"
            onClick={handleRefresh}
            disabled={loading}
            className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-sunken hover:bg-line text-ink rounded-xl text-xs font-medium transition-colors"
          >
            <RefreshCw className={`size-3.5 ${loading ? "animate-spin" : ""}`} />
            <span>Làm mới</span>
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="bg-surface p-4 sm:p-5 rounded-2xl border border-line/90 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted">Tổng số gia sư</span>
              <span className="size-8 rounded-xl bg-brand-soft text-brand-strong flex items-center justify-center">
                <GraduationCap className="size-4" />
              </span>
            </div>
            <p className="text-2xl font-bold font-heading text-ink mt-2">
              {stats.totalTutors}
            </p>
            <p className="text-xs text-muted mt-0.5">Tài khoản gia sư đã đăng ký</p>
          </div>

          <div className="bg-surface p-4 sm:p-5 rounded-2xl border border-line/90 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted">Đang hoạt động</span>
              <span className="size-8 rounded-xl bg-success-soft text-success-strong flex items-center justify-center">
                <CheckCircle2 className="size-4" />
              </span>
            </div>
            <p className="text-2xl font-bold font-heading text-success-strong mt-2">
              {stats.activeTutors ?? stats.totalTutors}
            </p>
            <p className="text-xs text-muted mt-0.5">Gia sư đang hoạt động</p>
          </div>

          <div className="bg-surface p-4 sm:p-5 rounded-2xl border border-line/90 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted">Đã bị khóa</span>
              <span className="size-8 rounded-xl bg-danger-soft text-danger-strong flex items-center justify-center">
                <Lock className="size-4" />
              </span>
            </div>
            <p className="text-2xl font-bold font-heading text-danger-strong mt-2">
              {stats.deactivatedTutors ?? 0}
            </p>
            <p className="text-xs text-muted mt-0.5">Tài khoản gia sư bị khóa</p>
          </div>
        </div>
      )}

      {/* Filters & Search */}
      <div className="bg-surface p-4 sm:p-5 rounded-3xl border border-line/90 shadow-xs space-y-4">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          {/* Status Select */}
          <div className="flex items-center gap-2">
            <Filter className="size-4 text-muted shrink-0" />
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value as "ALL" | AccountStatus);
                setPage(0);
              }}
              aria-label="Lọc theo trạng thái gia sư"
              className="text-xs bg-paper border border-line rounded-xl px-3 py-2 font-medium text-ink-2 focus:outline-none focus:ring-2 focus:ring-brand"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="DEACTIVATED">Đã bị khóa</option>
            </select>
          </div>
        </div>

        {/* Search Bar */}
        <div className="relative">
          <Search className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setPage(0);
            }}
            placeholder="Tìm kiếm gia sư theo họ tên hoặc địa chỉ email..."
            className="w-full pl-10 pr-4 py-2.5 bg-paper border border-line rounded-xl text-xs text-ink placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-brand focus:bg-surface transition-all"
          />
        </div>
      </div>

      {/* Table Section */}
      <div className="bg-surface rounded-3xl border border-line/90 shadow-xs overflow-hidden">
        {loading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-brand-strong animate-spin" />
            <span className="text-xs text-muted">Đang tải danh sách gia sư...</span>
          </div>
        ) : error ? (
          <div className="p-8 text-center space-y-3">
            <AlertCircle className="size-8 text-danger-strong mx-auto" />
            <p className="text-xs text-danger-strong font-medium">{error}</p>
            <button
              type="button"
              onClick={handleRefresh}
              className="px-4 py-2 bg-sunken hover:bg-line text-xs font-medium rounded-xl text-ink-2"
            >
              Thử lại
            </button>
          </div>
        ) : users.length === 0 ? (
          <div className="p-8 sm:p-12">
            <EmptyState
              icon={GraduationCap}
              badge="KHÔNG CÓ DỮ LIỆU"
              title="Không tìm thấy gia sư nào"
              description={
                searchQuery
                  ? `Không có gia sư nào phù hợp với từ khóa "${searchQuery}". Hãy thử tìm kiếm với từ khóa khác.`
                  : "Chưa có tài khoản gia sư nào trong tiêu chí lựa chọn hiện tại."
              }
              action={{
                label: "Xóa bộ lọc & Tải lại",
                onClick: () => {
                  setSearchQuery("");
                  setSelectedStatus("ALL");
                  setPage(0);
                },
                icon: RefreshCw,
              }}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-paper border-b border-line text-muted text-xs font-medium">
                <tr>
                  <th className="p-4 sm:px-6">Gia sư</th>
                  <th className="p-4 sm:px-6">Vai trò</th>
                  <th className="p-4 sm:px-6">Trạng thái</th>
                  <th className="p-4 sm:px-6">Ngày tham gia</th>
                  <th className="p-4 sm:px-6 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-paper/60 transition-colors">
                    {/* Tutor info */}
                    <td className="p-4 sm:px-6">
                      <div className="flex items-center gap-3">
                        <div className="size-9 rounded-xl overflow-hidden bg-sunken border border-line flex items-center justify-center shrink-0">
                          {user.avatarUrl ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img
                              src={user.avatarUrl}
                              alt={user.fullName}
                              className="size-full object-cover"
                            />
                          ) : (
                            <span className="font-medium text-xs text-ink-2">
                              {user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}
                            </span>
                          )}
                        </div>
                        <div className="min-w-0">
                          <p className="font-medium text-ink truncate">
                            {user.fullName || "Chưa cập nhật tên"}
                          </p>
                          <p className="text-xs text-muted truncate">{user.email}</p>
                        </div>
                      </div>
                    </td>

                    {/* Role */}
                    <td className="p-4 sm:px-6">
                      <UserRoleBadge role={user.role} />
                    </td>

                    {/* Status */}
                    <td className="p-4 sm:px-6">
                      <UserStatusBadge status={user.status} />
                    </td>

                    {/* Joined Date */}
                    <td className="p-4 sm:px-6 text-ink-2">
                      {formatDate(user.createdAt)}
                    </td>

                    {/* Actions */}
                    <td className="p-4 sm:px-6 text-right">
                      {user.status === "ACTIVE" ? (
                        <button
                          type="button"
                          onClick={() => handleOpenLock(user)}
                          aria-label={`Khóa tài khoản ${user.fullName || user.email}`}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-danger-soft hover:bg-danger-soft text-danger-strong rounded-xl text-xs font-medium transition-colors border border-danger-line/80"
                        >
                          <Lock className="size-3.5" />
                          <span>Khóa</span>
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => handleOpenUnlock(user)}
                          aria-label={`Mở khóa tài khoản ${user.fullName || user.email}`}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-success-soft hover:bg-success-soft text-success-strong rounded-xl text-xs font-medium transition-colors border border-success-line/80"
                        >
                          <Unlock className="size-3.5" />
                          <span>Mở khóa</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between p-4 border-t border-line bg-paper/50">
                <span className="text-xs text-muted">
                  Hiển thị {users.length} trên tổng số {totalElements} gia sư
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="p-2 rounded-xl border border-line bg-surface hover:bg-paper disabled:opacity-50 text-ink-2 transition-colors"
                  >
                    <ChevronLeft className="size-4" />
                  </button>
                  <span className="text-xs font-medium text-ink-2 px-2">
                    Trang {page + 1} / {totalPages}
                  </span>
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="p-2 rounded-xl border border-line bg-surface hover:bg-paper disabled:opacity-50 text-ink-2 transition-colors"
                  >
                    <ChevronRight className="size-4" />
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Confirmation Modal */}
      <UserActionModal
        user={actionUser}
        action={actionType}
        isOpen={Boolean(actionUser && actionType)}
        onClose={handleCloseModal}
        onConfirm={handleConfirmAction}
      />
    </div>
  );
}
