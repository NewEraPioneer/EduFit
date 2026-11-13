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
    badgeColor: "bg-action-soft text-action-strong border-action-line",
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
    badgeColor: "bg-success-soft text-success-strong border-success-line",
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
    badgeColor: "bg-danger-soft text-danger-strong border-danger-line",
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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-surface p-6 rounded-3xl border border-line/90 shadow-xs">
        <div>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-soft text-brand-strong rounded-full text-xs font-medium mb-2">
            <ShieldCheck className="size-3.5 text-brand-strong" />
            <span>Trung tâm kiểm duyệt hồ sơ (KYC)</span>
          </div>
          <h2 className="font-heading text-xl sm:text-2xl font-bold text-ink">
            Hàng đợi duyệt và thu hồi bằng cấp gia sư
          </h2>
          <p className="text-xs text-muted mt-1">
            Quản lý thẩm định, phê duyệt và thu hồi bằng cấp gia sư
          </p>
        </div>

        <button
          type="button"
          onClick={loadQueue}
          disabled={loading}
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-sunken hover:bg-line text-ink rounded-xl text-xs font-medium transition-colors shrink-0 cursor-pointer"
        >
          <RefreshCw className={`size-3.5 ${loading ? "animate-spin" : ""}`} />
          <span>Làm mới</span>
        </button>
      </div>

      {/* 3 Tabs Bar */}
      <div className="flex border-b border-line bg-surface px-4 pt-2 rounded-t-3xl border-t border-x border-line/90 gap-2">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-3 text-xs font-medium border-b-2 transition-all cursor-pointer ${
                isActive
                  ? "border-brand text-brand-strong bg-brand-soft/50 rounded-t-xl"
                  : "border-transparent text-muted hover:text-ink hover:bg-paper rounded-t-xl"
              }`}
            >
              <Icon
                className={`size-4 ${
                  isActive
                    ? tab.id === "PENDING"
                      ? "text-action-strong"
                      : tab.id === "APPROVED"
                      ? "text-success-strong"
                      : "text-danger-strong"
                    : "text-muted"
                }`}
              />
              <span>{tab.label}</span>
              {isActive && totalElements > 0 && (
                <span className="ml-1 px-2 py-0.5 rounded-full text-xs font-medium bg-line/70 text-ink">
                  {totalElements}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Content Area */}
      <div className="bg-surface rounded-b-3xl rounded-t-none border-b border-x border-line/90 shadow-xs overflow-hidden">
        {loading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-brand-strong animate-spin" />
            <span className="text-xs text-muted">
              Đang tải danh sách hồ sơ {currentTabConfig.label.toLowerCase()} từ máy chủ...
            </span>
          </div>
        ) : items.length === 0 ? (
          <div className="p-8 sm:p-12">
            <EmptyState
              icon={currentTabConfig.icon}
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
              <thead className="bg-paper border-b border-line text-muted text-xs font-medium">
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
              <tbody className="divide-y divide-line">
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
                      className="hover:bg-paper/60 transition-colors"
                    >
                      {/* Request ID */}
                      <td className="p-4 sm:px-6">
                        <div className="font-mono font-medium text-ink text-xs">
                          {reqId}
                        </div>
                        <div className="text-xs text-muted font-mono">
                          ID: {it.tutorId}
                        </div>
                      </td>

                      {/* Tutor Details */}
                      <td className="p-4 sm:px-6">
                        <div className="flex items-start gap-2">
                          <div className="size-7 rounded-full bg-sunken text-ink-2 flex items-center justify-center font-medium text-xs shrink-0 mt-0.5">
                            <User className="size-3.5" />
                          </div>
                          <div>
                            <div className="font-medium text-ink">
                              {it.tutorName || "Gia sư"}
                            </div>
                            <div className="text-xs text-muted">
                              {it.tutorEmail || it.email || "—"}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Credential info */}
                      <td className="p-4 sm:px-6">
                        <div className="font-medium text-ink">
                          {it.institution || "Chưa cập nhật cơ sở"}
                        </div>
                        <div className="text-xs text-muted">
                          <span className="font-medium text-brand-strong">
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
                                className="inline-flex items-center gap-1 px-2 py-0.5 bg-sunken hover:bg-line text-ink-2 rounded-md text-xs font-medium transition-colors"
                                title="Xem tài liệu đính kèm"
                              >
                                <FileText className="size-3 text-brand-strong" />
                                <span>Tệp {idx + 1}</span>
                                <ExternalLink className="size-2 text-muted" />
                              </a>
                            ))}
                          </div>
                        ) : (
                          <span className="text-xs text-muted italic">
                            Chưa có file
                          </span>
                        )}
                      </td>

                      {/* Dates */}
                      <td className="p-4 sm:px-6 text-muted">
                        <div className="flex items-center gap-1.5">
                          <Calendar className="size-3.5 text-muted" />
                          <span>
                            {activeTab === "PENDING"
                              ? new Date(it.submittedAt).toLocaleString("vi-VN")
                              : it.reviewedAt
                              ? new Date(it.reviewedAt).toLocaleString("vi-VN")
                              : new Date(it.submittedAt).toLocaleString("vi-VN")}
                          </span>
                        </div>
                        {it.reviewedBy && (
                          <div className="text-xs text-muted font-mono mt-0.5">
                            Bởi: {it.reviewedBy}
                          </div>
                        )}
                      </td>

                      {/* Rejection / Revocation Reason (for REJECTED tab) */}
                      {activeTab === "REJECTED" && (
                        <td className="p-4 sm:px-6 max-w-xs">
                          {isRevoked ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium bg-danger-soft text-danger-strong border border-danger-line mb-1">
                              <AlertTriangle className="size-3 text-danger-strong" />
                              <span>ĐÃ THU HỒI</span>
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium bg-sunken text-ink border border-line mb-1">
                              <span>TỪ CHỐI</span>
                            </span>
                          )}
                          <p className="text-xs text-ink-2 italic truncate" title={displayReason || ""}>
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
                            className="px-3.5 py-1.5 bg-brand hover:bg-brand-deep text-white rounded-xl font-medium text-xs shadow-2xs transition-all inline-flex items-center gap-1.5 cursor-pointer"
                          >
                            <Eye className="size-3.5" />
                            <span>Thẩm định</span>
                          </button>
                        )}

                        {activeTab === "APPROVED" && (
                          <button
                            type="button"
                            onClick={() => handleOpenRevoke(it)}
                            className="px-3 py-1.5 bg-danger-soft hover:bg-danger-soft text-danger-strong border border-danger-line hover:border-danger-line rounded-xl font-medium text-xs transition-all inline-flex items-center gap-1.5 cursor-pointer"
                          >
                            <ShieldAlert className="size-3.5 text-danger-strong" />
                            <span>Thu hồi bằng cấp</span>
                          </button>
                        )}

                        {activeTab === "REJECTED" && (
                          <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-sunken text-ink-2 border border-line">
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
