"use client";

import React from "react";
import { ReviewKycModal } from "./ReviewKycModal";
import { RevokeKycModal } from "./RevokeKycModal";
import { useAdminKycQueue, KycQueueTab } from "../hooks/useAdminKycQueue";
import {
  ShieldCheck,
  Calendar,
  Eye,
  RefreshCw,
  Loader2,
  Clock,
  CheckCircle2,
  XCircle,
  FileText,
  ExternalLink,
  ShieldAlert,
  User,
  AlertTriangle,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";
import { CREDENTIAL_TYPE_LABELS } from "../schemas/kycSchemas";

/**
 * =========================================================================
 * SPRING BOOT API INTEGRATION:
 * =========================================================================
 * Endpoint: GET /api/v1/admin/verifications?status=PENDING|APPROVED|REJECTED
 * Controller: vn.edufit.verification.web.AdminVerificationController#getVerifications
 * Response: PendingVerificationQueueResponse { items, page, size, totalElements, totalPages }
 * Security: @PreAuthorize("hasRole('ADMIN')")
 * =========================================================================
 */

interface TabConfig {
  id: KycQueueTab;
  label: string;
  icon: LucideIcon;
  theme: "purple" | "emerald" | "amber" | "neutral";
  badgeColor: string;
  emptyTitle: string;
  emptyDesc: string;
  emptyBadge: string;
}

const TABS: TabConfig[] = [
  {
    id: "PENDING",
    label: "Chờ duyệt",
    icon: Clock,
    theme: "purple",
    badgeColor: "bg-amber-100 text-amber-900 border-amber-200",
    emptyTitle: "Không có hồ sơ nào đang chờ duyệt",
    emptyDesc:
      "Tất cả hồ sơ bằng cấp và giấy tờ định danh của gia sư đã được xem xét và xử lý. Khi có gia sư nộp hồ sơ mới, dữ liệu sẽ tự động xuất hiện ở đây.",
    emptyBadge: "HÀNG ĐỢI HOÀN TẤT",
  },
  {
    id: "APPROVED",
    label: "Đã duyệt",
    icon: CheckCircle2,
    theme: "emerald",
    badgeColor: "bg-emerald-100 text-emerald-900 border-emerald-200",
    emptyTitle: "Chưa có hồ sơ nào được phê duyệt",
    emptyDesc:
      "Danh sách các văn bằng, chứng chỉ gia sư đã được Quản trị viên thẩm định và cấp huy hiệu VERIFIED sẽ hiển thị tại đây.",
    emptyBadge: "CHƯA CÓ DỮ LIỆU",
  },
  {
    id: "REJECTED",
    label: "Đã từ chối / Thu hồi",
    icon: XCircle,
    theme: "amber",
    badgeColor: "bg-rose-100 text-rose-900 border-rose-200",
    emptyTitle: "Không có hồ sơ nào bị từ chối hoặc thu hồi",
    emptyDesc:
      "Các hồ sơ không đạt chuẩn bị từ chối hoặc các bằng cấp bị thu hồi thẩm định sẽ được lưu trữ tại đây kèm lý do chi tiết.",
    emptyBadge: "DANH SÁCH RỖNG",
  },
];

export function AdminKycQueueView() {
  const {
    activeTab,
    setActiveTab,
    items,
    loading,
    totalElements,
    selectedItem,
    isReviewModalOpen,
    isRevokeModalOpen,
    loadQueue,
    handleOpenReview,
    handleCloseReview,
    handleReviewSuccess,
    handleOpenRevoke,
    handleCloseRevoke,
    handleRevokeSuccess,
  } = useAdminKycQueue();

  const currentTabConfig =
    TABS.find((t) => t.id === activeTab) || TABS[0];

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-3xl border border-stone-200/90 shadow-xs">
        <div>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-purple-50 text-purple-900 rounded-full text-xs font-bold mb-2">
            <ShieldCheck className="size-3.5 text-purple-700" />
            <span>Trung Tâm Kiểm Duyệt Hồ Sơ (KYC 3 Tabs)</span>
          </div>
          <h2 className="font-heading text-xl sm:text-2xl font-bold text-neutral-900">
            Hàng Đợi Duyệt & Thu Hồi Bằng Cấp Gia Sư
          </h2>
          <p className="text-xs text-neutral-500 mt-1">
            Quản lý quy trình thẩm định, phê duyệt và thu hồi bằng cấp theo quy định BR-05 & BR-06
          </p>
        </div>

        <button
          type="button"
          onClick={loadQueue}
          disabled={loading}
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-stone-100 hover:bg-stone-200 text-neutral-800 rounded-xl text-xs font-bold transition-colors shrink-0 cursor-pointer"
        >
          <RefreshCw className={`size-3.5 ${loading ? "animate-spin" : ""}`} />
          <span>Làm mới</span>
        </button>
      </div>

      {/* 3 Tabs Bar */}
      <div className="flex border-b border-stone-200 bg-white px-4 pt-2 rounded-t-3xl border-t border-x border-stone-200/90 gap-2">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${
                isActive
                  ? "border-purple-600 text-purple-700 bg-purple-50/50 rounded-t-xl"
                  : "border-transparent text-neutral-500 hover:text-neutral-800 hover:bg-stone-50 rounded-t-xl"
              }`}
            >
              <Icon
                className={`size-4 ${
                  isActive
                    ? tab.id === "PENDING"
                      ? "text-amber-600"
                      : tab.id === "APPROVED"
                      ? "text-emerald-600"
                      : "text-rose-600"
                    : "text-neutral-400"
                }`}
              />
              <span>{tab.label}</span>
              {isActive && totalElements > 0 && (
                <span className="ml-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-neutral-200/70 text-neutral-800">
                  {totalElements}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Content Area */}
      <div className="bg-white rounded-b-3xl rounded-t-none border-b border-x border-stone-200/90 shadow-xs overflow-hidden">
        {loading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-purple-600 animate-spin" />
            <span className="text-xs text-neutral-500">
              Đang tải danh sách hồ sơ {currentTabConfig.label.toLowerCase()} từ máy chủ...
            </span>
          </div>
        ) : items.length === 0 ? (
          <div className="p-8 sm:p-12">
            <EmptyState
              icon={currentTabConfig.icon}
              theme={currentTabConfig.theme}
              badge={currentTabConfig.emptyBadge}
              title={currentTabConfig.emptyTitle}
              description={currentTabConfig.emptyDesc}
              action={{
                label: "Làm mới danh sách",
                onClick: loadQueue,
                icon: RefreshCw,
              }}
              secondaryAction={{
                label: "Về trang tổng quan",
                href: "/admin/dashboard",
              }}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-stone-50 border-b border-stone-200 text-neutral-500 uppercase text-[10px] font-bold tracking-wider">
                <tr>
                  <th className="p-4 sm:px-6">Mã Yêu Cầu</th>
                  <th className="p-4 sm:px-6">Gia Sư</th>
                  <th className="p-4 sm:px-6">Bằng Cấp / Cơ Sở</th>
                  <th className="p-4 sm:px-6">Tài Liệu</th>
                  <th className="p-4 sm:px-6">
                    {activeTab === "PENDING" ? "Thời Gian Nộp" : "Thời Gian Xử Lý"}
                  </th>
                  {activeTab === "REJECTED" && (
                    <th className="p-4 sm:px-6">Lý Do / Kiểm Toán</th>
                  )}
                  <th className="p-4 sm:px-6 text-right">Thao Tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {items.map((it) => {
                  const reqId = it.requestId || it.id || "";
                  const isRevoked =
                    it.rejectionReason?.startsWith("[REVOKED]") || false;
                  const displayReason = isRevoked
                    ? it.rejectionReason?.replace("[REVOKED]", "").trim()
                    : it.rejectionReason;

                  return (
                    <tr
                      key={reqId}
                      className="hover:bg-stone-50/60 transition-colors"
                    >
                      {/* Request ID */}
                      <td className="p-4 sm:px-6">
                        <div className="font-mono font-semibold text-neutral-800 text-[11px]">
                          {reqId}
                        </div>
                        <div className="text-[10px] text-neutral-400 font-mono">
                          ID: {it.tutorId}
                        </div>
                      </td>

                      {/* Tutor Details */}
                      <td className="p-4 sm:px-6">
                        <div className="flex items-start gap-2">
                          <div className="size-7 rounded-full bg-stone-100 text-neutral-600 flex items-center justify-center font-bold text-[11px] shrink-0 mt-0.5">
                            <User className="size-3.5" />
                          </div>
                          <div>
                            <div className="font-bold text-neutral-900">
                              {it.tutorName || "Gia sư"}
                            </div>
                            <div className="text-[11px] text-neutral-500">
                              {it.tutorEmail || it.email || "—"}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Credential info */}
                      <td className="p-4 sm:px-6">
                        <div className="font-semibold text-neutral-900">
                          {it.institution || "Chưa cập nhật cơ sở"}
                        </div>
                        <div className="text-[11px] text-neutral-500">
                          <span className="font-medium text-purple-700">
                            {it.credentialType
                              ? CREDENTIAL_TYPE_LABELS[it.credentialType] ||
                                it.credentialType
                              : "Văn bằng / Chứng chỉ"}
                          </span>
                          {it.year ? ` • Năm ${it.year}` : ""}
                        </div>
                      </td>

                      {/* Document Preview URLs */}
                      <td className="p-4 sm:px-6">
                        {it.documentUrls && it.documentUrls.length > 0 ? (
                          <div className="flex flex-wrap gap-1.5">
                            {it.documentUrls.map((url, idx) => (
                              <a
                                key={idx}
                                href={url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 px-2 py-0.5 bg-stone-100 hover:bg-stone-200 text-neutral-700 rounded-md text-[10px] font-medium transition-colors"
                                title="Xem tài liệu đính kèm"
                              >
                                <FileText className="size-3 text-purple-600" />
                                <span>Tệp {idx + 1}</span>
                                <ExternalLink className="size-2 text-neutral-400" />
                              </a>
                            ))}
                          </div>
                        ) : (
                          <span className="text-[11px] text-neutral-400 italic">
                            Chưa có file
                          </span>
                        )}
                      </td>

                      {/* Dates */}
                      <td className="p-4 sm:px-6 text-neutral-500">
                        <div className="flex items-center gap-1.5">
                          <Calendar className="size-3.5 text-neutral-400" />
                          <span>
                            {activeTab === "PENDING"
                              ? new Date(it.submittedAt).toLocaleString("vi-VN")
                              : it.reviewedAt
                              ? new Date(it.reviewedAt).toLocaleString("vi-VN")
                              : new Date(it.submittedAt).toLocaleString("vi-VN")}
                          </span>
                        </div>
                        {it.reviewedBy && (
                          <div className="text-[10px] text-neutral-400 font-mono mt-0.5">
                            Bởi: {it.reviewedBy}
                          </div>
                        )}
                      </td>

                      {/* Rejection / Revocation Reason (for REJECTED tab) */}
                      {activeTab === "REJECTED" && (
                        <td className="p-4 sm:px-6 max-w-xs">
                          {isRevoked ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-rose-100 text-rose-900 border border-rose-200 mb-1">
                              <AlertTriangle className="size-3 text-rose-600" />
                              <span>ĐÃ THU HỒI</span>
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-neutral-100 text-neutral-800 border border-neutral-200 mb-1">
                              <span>TỪ CHỐI</span>
                            </span>
                          )}
                          <p className="text-[11px] text-neutral-700 italic truncate" title={displayReason || ""}>
                            "{displayReason || "Không có lý do chi tiết"}"
                          </p>
                        </td>
                      )}

                      {/* Actions */}
                      <td className="p-4 sm:px-6 text-right">
                        {activeTab === "PENDING" && (
                          <button
                            type="button"
                            onClick={() => handleOpenReview(it)}
                            className="px-3.5 py-1.5 bg-purple-700 hover:bg-purple-800 text-white rounded-xl font-bold text-xs shadow-2xs transition-all inline-flex items-center gap-1.5 cursor-pointer"
                          >
                            <Eye className="size-3.5" />
                            <span>Thẩm định</span>
                          </button>
                        )}

                        {activeTab === "APPROVED" && (
                          <button
                            type="button"
                            onClick={() => handleOpenRevoke(it)}
                            className="px-3 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 hover:border-rose-300 rounded-xl font-bold text-xs transition-all inline-flex items-center gap-1.5 cursor-pointer"
                          >
                            <ShieldAlert className="size-3.5 text-rose-600" />
                            <span>Thu hồi bằng cấp</span>
                          </button>
                        )}

                        {activeTab === "REJECTED" && (
                          <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-stone-100 text-neutral-600 border border-stone-200">
                            {isRevoked ? "Đã thu hồi" : "Đã từ chối"}
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Review Modal (for PENDING tab) */}
      <ReviewKycModal
        item={selectedItem}
        isOpen={isReviewModalOpen}
        onClose={handleCloseReview}
        onReviewed={handleReviewSuccess}
      />

      {/* Revoke Modal (for APPROVED tab) */}
      <RevokeKycModal
        item={selectedItem}
        isOpen={isRevokeModalOpen}
        onClose={handleCloseRevoke}
        onRevoked={handleRevokeSuccess}
      />
    </div>
  );
}
