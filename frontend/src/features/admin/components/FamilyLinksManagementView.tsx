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
        <div className="flex items-center gap-3 p-4 rounded-xl bg-emerald-50 text-emerald-800 border border-emerald-200 shadow-sm animate-in fade-in slide-in-from-top duration-300">
          <CheckCircle2 className="w-5 h-5 text-emerald-600 flex-shrink-0" />
          <p className="text-sm font-medium">{successToast}</p>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-gray-900">
              Liên kết Phụ huynh - Học sinh
            </h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-100 text-indigo-700">
              Quan hệ bảo trợ
            </span>
          </div>
          <p className="text-sm text-gray-500 mt-1">
            Theo dõi, giám sát và quản lý các mối quan hệ liên kết giám hộ giữa tài khoản Phụ huynh và Học sinh
          </p>
        </div>

        <button
          onClick={handleRefresh}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors shadow-sm self-start sm:self-auto"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          Làm mới
        </button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Links */}
        <div className="bg-white p-5 rounded-2xl border border-gray-200/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-indigo-50 text-indigo-600">
            <HeartHandshake className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              Tổng số liên kết
            </p>
            <p className="text-2xl font-bold text-gray-900 mt-0.5">
              {stats ? stats.totalLinks.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>

        {/* Active Links */}
        <div className="bg-white p-5 rounded-2xl border border-gray-200/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-emerald-50 text-emerald-600">
            <UserCheck className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              Đang kết nối
            </p>
            <p className="text-2xl font-bold text-emerald-600 mt-0.5">
              {stats ? stats.activeLinks.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>

        {/* Revoked Links */}
        <div className="bg-white p-5 rounded-2xl border border-gray-200/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-rose-50 text-rose-600">
            <UserX className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              Đã thu hồi / Hủy
            </p>
            <p className="text-2xl font-bold text-rose-600 mt-0.5">
              {stats ? stats.revokedLinks.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>

        {/* Unique Parents */}
        <div className="bg-white p-5 rounded-2xl border border-gray-200/80 shadow-sm flex items-center gap-4">
          <div className="p-3 rounded-xl bg-purple-50 text-purple-600">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              Phụ huynh bảo trợ
            </p>
            <p className="text-2xl font-bold text-purple-600 mt-0.5">
              {stats ? stats.uniqueParents.toLocaleString("vi-VN") : "--"}
            </p>
          </div>
        </div>
      </div>

      {/* Filters & Search */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          {/* Status Tabs */}
          <div className="flex items-center gap-1.5 p-1 bg-gray-100 rounded-lg self-start">
            <button
              onClick={() => {
                setSelectedStatus("ALL");
                setPage(0);
              }}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all ${
                selectedStatus === "ALL"
                  ? "bg-white text-gray-900 shadow-sm"
                  : "text-gray-600 hover:text-gray-900"
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
                  ? "bg-white text-emerald-700 shadow-sm"
                  : "text-gray-600 hover:text-emerald-700"
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
                  ? "bg-white text-rose-700 shadow-sm"
                  : "text-gray-600 hover:text-rose-700"
              }`}
            >
              Đã thu hồi
            </button>
          </div>

          {/* Search Box */}
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Tìm theo tên hoặc email phụ huynh, học sinh..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(0);
              }}
              className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
            {searchQuery && (
              <button
                onClick={() => {
                  setSearchQuery("");
                  setPage(0);
                }}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-xs font-semibold"
              >
                Xóa
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {loading ? (
          <div className="py-20 flex flex-col items-center justify-center text-gray-500">
            <Loader2 className="w-8 h-8 animate-spin text-indigo-600 mb-2" />
            <p className="text-sm">Đang tải dữ liệu liên kết...</p>
          </div>
        ) : error ? (
          <div className="py-16 px-4 text-center">
            <AlertCircle className="w-10 h-10 text-rose-500 mx-auto mb-3" />
            <p className="text-gray-800 font-medium">{error}</p>
            <button
              onClick={handleRefresh}
              className="mt-3 px-4 py-1.5 text-sm font-medium text-indigo-600 hover:underline"
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
                <tr className="bg-gray-50 border-b border-gray-200 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  <th className="py-3.5 px-4">Phụ huynh</th>
                  <th className="py-3.5 px-4 text-center">Trạng thái kết nối</th>
                  <th className="py-3.5 px-4">Học sinh</th>
                  <th className="py-3.5 px-4">Thời gian</th>
                  <th className="py-3.5 px-4 text-right">Hành động</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-sm">
                {links.map((link) => {
                  const isConfirmed = link.status === "CONFIRMED";
                  return (
                    <tr
                      key={link.linkId}
                      className="hover:bg-gray-50/80 transition-colors"
                      data-testid={`family-link-row-${link.linkId}`}
                    >
                      {/* Parent Column */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-purple-100 text-purple-700 font-semibold flex items-center justify-center text-xs flex-shrink-0">
                            {link.parentFullName
                              ? link.parentFullName.charAt(0).toUpperCase()
                              : "P"}
                          </div>
                          <div>
                            <div className="font-medium text-gray-900">
                              {link.parentFullName || "Chưa cập nhật tên"}
                            </div>
                            <div className="text-xs text-gray-500">
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
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                                Đang liên kết
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-50 text-rose-700 border border-rose-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-rose-500"></span>
                                Đã thu hồi
                              </span>
                            )}
                          </div>
                          <div className="text-[11px] text-gray-400 flex items-center gap-1">
                            Bảo trợ <ArrowRight className="w-3 h-3 text-gray-400" />
                          </div>
                        </div>
                      </td>

                      {/* Student Column */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-sky-100 text-sky-700 font-semibold flex items-center justify-center text-xs flex-shrink-0">
                            {link.studentFullName
                              ? link.studentFullName.charAt(0).toUpperCase()
                              : "H"}
                          </div>
                          <div>
                            <div className="font-medium text-gray-900">
                              {link.studentFullName || "Chưa cập nhật tên"}
                            </div>
                            <div className="text-xs text-gray-500">
                              {link.studentEmail || "--"}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Timestamps */}
                      <td className="py-3.5 px-4">
                        <div className="text-xs text-gray-600 flex items-center gap-1">
                          <Calendar className="w-3.5 h-3.5 text-gray-400" />
                          <span>Xác nhận: {formatDate(link.confirmedAt)}</span>
                        </div>
                        {link.revokedAt && (
                          <div className="text-[11px] text-rose-600 mt-0.5">
                            Hủy lúc: {formatDate(link.revokedAt)}
                          </div>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 px-4 text-right">
                        {isConfirmed ? (
                          <button
                            onClick={() => handleOpenRevokeModal(link)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-rose-600 bg-rose-50 hover:bg-rose-100 border border-rose-200 transition-colors"
                            data-testid={`revoke-btn-${link.linkId}`}
                          >
                            <ShieldAlert className="w-3.5 h-3.5" />
                            Thu hồi
                          </button>
                        ) : (
                          <button
                            onClick={() => setDetailLink(link)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-gray-600 bg-gray-100 hover:bg-gray-200 transition-colors"
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
          <div className="p-4 border-t border-gray-200 flex items-center justify-between text-sm text-gray-600">
            <span>
              Tổng cộng <strong>{totalElements}</strong> liên kết (Trang{" "}
              {page + 1}/{totalPages})
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
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
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm overflow-y-auto"
          data-testid="revoke-confirm-modal"
        >
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-gray-100 animate-in fade-in zoom-in-95 duration-150 my-auto">
            <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center mb-4">
              <ShieldAlert className="w-6 h-6" />
            </div>

            <h3 className="text-lg font-bold text-gray-900">
              Xác nhận thu hồi liên kết bảo trợ
            </h3>
            <p className="text-sm text-gray-500 mt-1">
              Bạn đang chuẩn bị hủy liên kết giữa Phụ huynh{" "}
              <strong className="text-gray-800">
                {selectedLinkToRevoke.parentFullName}
              </strong>{" "}
              và Học sinh{" "}
              <strong className="text-gray-800">
                {selectedLinkToRevoke.studentFullName}
              </strong>
              . Hành động này sẽ ngắt quyền giám sát học tập của phụ huynh.
            </p>

            {revokeError && (
              <div className="mt-3 p-3 rounded-lg bg-rose-50 text-rose-700 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{revokeError}</span>
              </div>
            )}

            <div className="mt-4">
              <label className="block text-xs font-semibold text-gray-700 mb-1">
                Lý do thu hồi (Bắt buộc cho Quản trị viên)
              </label>
              <textarea
                rows={3}
                placeholder="Nhập lý do thu hồi (vd: Yêu cầu xác thực tài khoản sai lệch, tranh chấp khiếu nại...)"
                value={revokeReason}
                onChange={(e) => setRevokeReason(e.target.value)}
                className="w-full p-2.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
              />
            </div>

            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={handleCloseRevokeModal}
                disabled={isSubmittingRevoke}
                className="px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmRevoke}
                disabled={isSubmittingRevoke}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-rose-600 hover:bg-rose-700 rounded-lg shadow-sm transition-colors disabled:opacity-50"
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
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm overflow-y-auto"
          data-testid="link-detail-modal"
        >
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-gray-100 my-auto">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center">
                <Info className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-gray-900">
                  Thông tin thu hồi liên kết
                </h3>
                <p className="text-xs text-gray-500">
                  Chi tiết về việc chấm dứt quan hệ liên kết
                </p>
              </div>
            </div>

            <div className="space-y-3 text-sm bg-gray-50 p-3.5 rounded-xl border border-gray-100">
              <div>
                <span className="text-xs text-gray-500 block">Phụ huynh</span>
                <span className="font-medium text-gray-800">
                  {detailLink.parentFullName} ({detailLink.parentEmail})
                </span>
              </div>
              <div>
                <span className="text-xs text-gray-500 block">Học sinh</span>
                <span className="font-medium text-gray-800">
                  {detailLink.studentFullName} ({detailLink.studentEmail})
                </span>
              </div>
              <div>
                <span className="text-xs text-gray-500 block">Thời gian xác nhận</span>
                <span className="text-gray-700">
                  {formatDate(detailLink.confirmedAt)}
                </span>
              </div>
              <div>
                <span className="text-xs text-gray-500 block">Thời gian thu hồi</span>
                <span className="text-rose-600 font-medium">
                  {formatDate(detailLink.revokedAt)}
                </span>
              </div>
              <div className="pt-1 border-t border-gray-200">
                <span className="text-xs text-gray-500 block">Lý do thu hồi</span>
                <p className="text-gray-800 font-medium mt-0.5 whitespace-pre-wrap">
                  {detailLink.revokeReason || "Không có lý do cụ thể"}
                </p>
              </div>
            </div>

            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={() => setDetailLink(null)}
                className="px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
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
