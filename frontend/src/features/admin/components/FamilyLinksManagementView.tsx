"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  HeartHandshake,
  Search,
  RefreshCw,
  AlertCircle,
  CheckCircle2,
  Filter,
  Loader2,
  ChevronLeft,
  ChevronRight,
  ArrowRight,
  UserCheck,
  UserX,
  Eye,
  Info,
  Calendar,
  ShieldAlert,
} from "lucide-react";
import { adminUserApi } from "../api/adminUserApi";
import type {
  AdminParentStudentLink,
  AdminParentStudentLinkStats,
  FamilyLinkStatus,
} from "../types";
import { EmptyState } from "@/shared/ui/EmptyState";

export function FamilyLinksManagementView() {
  const [links, setLinks] = useState<AdminParentStudentLink[]>([]);
  const [stats, setStats] = useState<AdminParentStudentLinkStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  // Filters & Pagination
  const [selectedStatus, setSelectedStatus] = useState<FamilyLinkStatus>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Revoke Modal state
  const [selectedLinkToRevoke, setSelectedLinkToRevoke] = useState<AdminParentStudentLink | null>(null);
  const [revokeReason, setRevokeReason] = useState("");
  const [isSubmittingRevoke, setIsSubmittingRevoke] = useState(false);
  const [revokeError, setRevokeError] = useState<string | null>(null);

  // Detail Modal state (for revoked links)
  const [detailLink, setDetailLink] = useState<AdminParentStudentLink | null>(null);

  const fetchStats = useCallback(async () => {
    try {
      const res = await adminUserApi.getFamilyLinkStats();
      if (res.success && res.data) {
        setStats(res.data);
      }
    } catch (e) {
      console.error("Lỗi khi tải thống kê liên kết:", e);
    }
  }, []);

  const fetchLinks = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const statusParam = selectedStatus === "ALL" ? undefined : selectedStatus;
      const res = await adminUserApi.getFamilyLinks({
        status: statusParam,
        search: searchQuery.trim() || undefined,
        page,
        size: 15,
      });

      if (res.success && res.data) {
        setLinks(res.data.items || []);
        setTotalPages(res.data.totalPages || 1);
        setTotalElements(res.data.totalElements || 0);
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Không thể tải danh sách liên kết gia đình");
      }
    } finally {
      setLoading(false);
    }
  }, [selectedStatus, searchQuery, page]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  useEffect(() => {
    fetchLinks();
  }, [fetchLinks]);

  const handleRefresh = () => {
    fetchStats();
    fetchLinks();
  };

  const handleOpenRevokeModal = (link: AdminParentStudentLink) => {
    setSelectedLinkToRevoke(link);
    setRevokeReason("");
    setRevokeError(null);
  };

  const handleCloseRevokeModal = () => {
    setSelectedLinkToRevoke(null);
    setRevokeReason("");
    setRevokeError(null);
  };

  const handleConfirmRevoke = async () => {
    if (!selectedLinkToRevoke) return;

    if (!revokeReason.trim()) {
      setRevokeError("Vui lòng nhập lý do thu hồi liên kết");
      return;
    }

    try {
      setIsSubmittingRevoke(true);
      setRevokeError(null);

      const res = await adminUserApi.revokeFamilyLink(
        selectedLinkToRevoke.linkId,
        revokeReason.trim()
      );

      if (res.success) {
        setSuccessToast(
          `Đã hủy liên kết giữa ${selectedLinkToRevoke.parentFullName} và ${selectedLinkToRevoke.studentFullName} thành công!`
        );
        handleCloseRevokeModal();
        fetchStats();
        fetchLinks();
        setTimeout(() => setSuccessToast(null), 5000);
      } else {
        setRevokeError(res.message || "Không thể hủy liên kết");
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setRevokeError(err.message);
      } else {
        setRevokeError("Đã xảy ra lỗi khi hủy liên kết");
      }
    } finally {
      setIsSubmittingRevoke(false);
    }
  };

  const formatDate = (isoString?: string | null) => {
    if (!isoString) return "--";
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="space-y-6" data-testid="family-links-view">
      {/* Toast Notification */}
      {successToast && (
        <div className="flex items-center gap-3 p-4 rounded-xl bg-success-soft text-success-strong border border-success-line shadow-sm animate-in fade-in slide-in-from-top duration-300">
          <CheckCircle2 className="w-5 h-5 text-success-strong flex-shrink-0" />
          <p className="text-sm font-medium">{successToast}</p>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-ink">
              Liên kết Phụ huynh - Học sinh
            </h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-brand-soft text-brand-strong">
              Quan hệ bảo trợ
            </span>
          </div>
          <p className="text-sm text-muted mt-1">
            Theo dõi, giám sát và quản lý các mối quan hệ liên kết giám hộ giữa tài khoản Phụ huynh và Học sinh
          </p>
        </div>

        <button
          onClick={handleRefresh}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 border border-line-strong rounded-lg text-sm font-medium text-ink-2 bg-surface hover:bg-paper transition-colors shadow-sm self-start sm:self-auto"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          Làm mới
        </button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Links */}
        <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-brand-soft text-brand-strong">
            <HeartHandshake className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-muted">
              Tổng số liên kết
            </p>
            <p className="text-2xl font-bold text-ink mt-0.5">
              {stats ? stats.totalLinks.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>

        {/* Active Links */}
        <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-success-soft text-success-strong">
            <UserCheck className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-muted">
              Đang kết nối
            </p>
            <p className="text-2xl font-bold text-success-strong mt-0.5">
              {stats ? stats.activeLinks.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>

        {/* Revoked Links */}
        <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-danger-soft text-danger-strong">
            <UserX className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-muted">
              Đã thu hồi / Hủy
            </p>
            <p className="text-2xl font-bold text-danger-strong mt-0.5">
              {stats ? stats.revokedLinks.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>

        {/* Unique Parents */}
        <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-brand-soft text-brand-strong">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-muted">
              Phụ huynh bảo trợ
            </p>
            <p className="text-2xl font-bold text-brand-strong mt-0.5">
              {stats ? stats.uniqueParents.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>
      </div>

      {/* Filters & Search */}
      <div className="bg-surface p-4 rounded-xl border border-line shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          {/* Status Tabs */}
          <div className="flex items-center gap-1.5 p-1 bg-sunken rounded-lg self-start">
            <button
              onClick={() => {
                setSelectedStatus("ALL");
                setPage(0);
              }}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all ${
                selectedStatus === "ALL"
                  ? "bg-surface text-ink shadow-sm"
                  : "text-ink-2 hover:text-ink"
              }`}
            >
              Tất cả
            </button>
            <button
              onClick={() => {
                setSelectedStatus("CONFIRMED");
                setPage(0);
              }}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all ${
                selectedStatus === "CONFIRMED"
                  ? "bg-surface text-success-strong shadow-sm"
                  : "text-ink-2 hover:text-success-strong"
              }`}
            >
              Đang liên kết
            </button>
            <button
              onClick={() => {
                setSelectedStatus("REVOKED");
                setPage(0);
              }}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all ${
                selectedStatus === "REVOKED"
                  ? "bg-surface text-danger-strong shadow-sm"
                  : "text-ink-2 hover:text-danger-strong"
              }`}
            >
              Đã thu hồi
            </button>
          </div>

          {/* Search Box */}
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-muted absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Tìm theo tên hoặc email phụ huynh, học sinh..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(0);
              }}
              className="w-full pl-9 pr-4 py-2 border border-line-strong rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand focus:border-brand"
            />
            {searchQuery && (
              <button
                onClick={() => {
                  setSearchQuery("");
                  setPage(0);
                }}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted hover:text-ink-2 text-xs font-medium"
              >
                Xóa
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Table */}
      <div className="bg-surface rounded-xl border border-line shadow-sm overflow-hidden">
        {loading ? (
          <div className="py-20 flex flex-col items-center justify-center text-muted">
            <Loader2 className="w-8 h-8 animate-spin text-brand-strong mb-2" />
            <p className="text-sm">Đang tải dữ liệu liên kết...</p>
          </div>
        ) : error ? (
          <div className="py-16 px-4 text-center">
            <AlertCircle className="w-10 h-10 text-danger-strong mx-auto mb-3" />
            <p className="text-ink font-medium">{error}</p>
            <button
              onClick={handleRefresh}
              className="mt-3 px-4 py-1.5 text-sm font-medium text-brand-strong hover:underline"
            >
              Thử lại
            </button>
          </div>
        ) : links.length === 0 ? (
          <div className="py-16">
            <EmptyState
              icon={HeartHandshake}
              title="Không có liên kết nào"
              description={
                searchQuery
                  ? "Không tìm thấy dữ liệu liên kết phù hợp với tìm kiếm."
                  : "Chưa có liên kết phụ huynh - học sinh nào trong hệ thống."
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-paper border-b border-line text-xs font-medium text-muted">
                  <th className="py-3.5 px-4">Phụ huynh</th>
                  <th className="py-3.5 px-4 text-center">Trạng thái kết nối</th>
                  <th className="py-3.5 px-4">Học sinh</th>
                  <th className="py-3.5 px-4">Thời gian</th>
                  <th className="py-3.5 px-4 text-right">Hành động</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line text-sm">
                {links.map((link) => {
                  const isConfirmed = link.status === "CONFIRMED";
                  return (
                    <tr
                      key={link.linkId}
                      className="hover:bg-paper/80 transition-colors"
                      data-testid={`family-link-row-${link.linkId}`}
                    >
                      {/* Parent Column */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-brand-soft text-brand-strong font-medium flex items-center justify-center text-xs flex-shrink-0">
                            {link.parentFullName
                              ? link.parentFullName.charAt(0).toUpperCase()
                              : "P"}
                          </div>
                          <div>
                            <div className="font-medium text-ink">
                              {link.parentFullName || "Chưa cập nhật tên"}
                            </div>
                            <div className="text-xs text-muted">
                              {link.parentEmail || "--"}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Connection Status & Direction Arrow */}
                      <td className="py-3.5 px-4 text-center">
                        <div className="inline-flex flex-col items-center gap-1">
                          <div className="flex items-center gap-1.5 text-xs font-medium">
                            {isConfirmed ? (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-success-soft text-success-strong border border-success-line">
                                <span className="w-1.5 h-1.5 rounded-full bg-success"></span>
                                Đang liên kết
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-danger-soft text-danger-strong border border-danger-line">
                                <span className="w-1.5 h-1.5 rounded-full bg-danger"></span>
                                Đã thu hồi
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-muted flex items-center gap-1">
                            Bảo trợ <ArrowRight className="w-3 h-3 text-muted" />
                          </div>
                        </div>
                      </td>

                      {/* Student Column */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-brand-soft text-brand-strong font-medium flex items-center justify-center text-xs flex-shrink-0">
                            {link.studentFullName
                              ? link.studentFullName.charAt(0).toUpperCase()
                              : "H"}
                          </div>
                          <div>
                            <div className="font-medium text-ink">
                              {link.studentFullName || "Chưa cập nhật tên"}
                            </div>
                            <div className="text-xs text-muted">
                              {link.studentEmail || "--"}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Timestamps */}
                      <td className="py-3.5 px-4">
                        <div className="text-xs text-ink-2 flex items-center gap-1">
                          <Calendar className="w-3.5 h-3.5 text-muted" />
                          <span>Xác nhận: {formatDate(link.confirmedAt)}</span>
                        </div>
                        {link.revokedAt && (
                          <div className="text-xs text-danger-strong mt-0.5">
                            Hủy lúc: {formatDate(link.revokedAt)}
                          </div>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 px-4 text-right">
                        {isConfirmed ? (
                          <button
                            onClick={() => handleOpenRevokeModal(link)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-danger-strong bg-danger-soft hover:bg-danger-soft border border-danger-line transition-colors"
                            data-testid={`revoke-btn-${link.linkId}`}
                          >
                            <ShieldAlert className="w-3.5 h-3.5" />
                            Thu hồi
                          </button>
                        ) : (
                          <button
                            onClick={() => setDetailLink(link)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-ink-2 bg-sunken hover:bg-line transition-colors"
                            data-testid={`view-reason-btn-${link.linkId}`}
                          >
                            <Eye className="w-3.5 h-3.5" />
                            Xem lý do
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {!loading && totalPages > 1 && (
          <div className="p-4 border-t border-line flex items-center justify-between text-sm text-ink-2">
            <span>
              Tổng cộng <strong>{totalElements}</strong> liên kết (Trang{" "}
              {page + 1}/{totalPages})
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 border border-line-strong rounded-lg hover:bg-paper disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 border border-line-strong rounded-lg hover:bg-paper disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Revoke Confirmation Modal */}
      {selectedLinkToRevoke && (
        <div
          className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/50 backdrop-blur-sm overflow-y-auto"
          data-testid="revoke-confirm-modal"
        >
          <div className="bg-surface rounded-2xl max-w-dialog-confirm w-full p-6 shadow-xl border border-line animate-in fade-in zoom-in-95 duration-150 my-auto">
            <div className="w-12 h-12 rounded-full bg-danger-soft text-danger-strong flex items-center justify-center mb-4">
              <ShieldAlert className="w-6 h-6" />
            </div>

            <h3 className="text-lg font-bold text-ink">
              Xác nhận thu hồi liên kết bảo trợ
            </h3>
            <p className="text-sm text-muted mt-1">
              Bạn đang chuẩn bị hủy liên kết giữa Phụ huynh{" "}
              <strong className="text-ink">
                {selectedLinkToRevoke.parentFullName}
              </strong>{" "}
              và Học sinh{" "}
              <strong className="text-ink">
                {selectedLinkToRevoke.studentFullName}
              </strong>
              . Hành động này sẽ ngắt quyền giám sát học tập của phụ huynh.
            </p>

            {revokeError && (
              <div className="mt-3 p-3 rounded-lg bg-danger-soft text-danger-strong text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{revokeError}</span>
              </div>
            )}

            <div className="mt-4">
              <label className="block text-xs font-medium text-ink-2 mb-1">
                Lý do thu hồi (Bắt buộc cho Quản trị viên)
              </label>
              <textarea
                rows={3}
                placeholder="Nhập lý do thu hồi (vd: Yêu cầu xác thực tài khoản sai lệch, tranh chấp khiếu nại...)"
                value={revokeReason}
                onChange={(e) => setRevokeReason(e.target.value)}
                className="w-full p-2.5 text-sm border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-danger focus:border-danger"
              />
            </div>

            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={handleCloseRevokeModal}
                disabled={isSubmittingRevoke}
                className="px-4 py-2 text-sm font-medium text-ink-2 hover:bg-sunken rounded-lg transition-colors"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmRevoke}
                disabled={isSubmittingRevoke}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-danger hover:bg-danger-deep rounded-lg shadow-sm transition-colors disabled:opacity-50"
              >
                {isSubmittingRevoke && (
                  <Loader2 className="w-4 h-4 animate-spin" />
                )}
                Xác nhận thu hồi
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Revoke Detail Modal */}
      {detailLink && (
        <div
          className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/50 backdrop-blur-sm overflow-y-auto"
          data-testid="link-detail-modal"
        >
          <div className="bg-surface rounded-2xl max-w-dialog-confirm w-full p-6 shadow-xl border border-line my-auto">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-danger-soft text-danger-strong flex items-center justify-center">
                <Info className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-medium text-ink">
                  Thông tin thu hồi liên kết
                </h3>
                <p className="text-xs text-muted">
                  Chi tiết về việc chấm dứt quan hệ liên kết
                </p>
              </div>
            </div>

            <div className="space-y-3 text-sm bg-paper p-3.5 rounded-xl border border-line">
              <div>
                <span className="text-xs text-muted block">Phụ huynh</span>
                <span className="font-medium text-ink">
                  {detailLink.parentFullName} ({detailLink.parentEmail})
                </span>
              </div>
              <div>
                <span className="text-xs text-muted block">Học sinh</span>
                <span className="font-medium text-ink">
                  {detailLink.studentFullName} ({detailLink.studentEmail})
                </span>
              </div>
              <div>
                <span className="text-xs text-muted block">Thời gian xác nhận</span>
                <span className="text-ink-2">
                  {formatDate(detailLink.confirmedAt)}
                </span>
              </div>
              <div>
                <span className="text-xs text-muted block">Thời gian thu hồi</span>
                <span className="text-danger-strong font-medium">
                  {formatDate(detailLink.revokedAt)}
                </span>
              </div>
              <div className="pt-1 border-t border-line">
                <span className="text-xs text-muted block">Lý do thu hồi</span>
                <p className="text-ink font-medium mt-0.5 whitespace-pre-wrap">
                  {detailLink.revokeReason || "Không có lý do cụ thể"}
                </p>
              </div>
            </div>

            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={() => setDetailLink(null)}
                className="px-4 py-2 text-sm font-medium text-ink-2 hover:bg-sunken rounded-lg transition-colors"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
