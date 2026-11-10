"use client";

import React from "react";
import Link from "next/link";
import { UserCheck, ShieldCheck, Sparkles } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

export interface PendingRequestItem {
  name: string;
  subject: string;
  note: string;
  match: string;
}

interface TutorPendingRequestsProps {
  requests?: PendingRequestItem[];
}

export function TutorPendingRequests({ requests = [] }: TutorPendingRequestsProps) {
  if (requests.length === 0) {
    return (
      <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
              Yêu cầu kết nối đang chờ phản hồi
            </h3>
            <span className="size-5 bg-line text-ink-2 font-medium text-xs rounded-full flex items-center justify-center">
              0
            </span>
          </div>
          <span className="text-xs text-muted font-medium">Hàng đợi</span>
        </div>

        <EmptyState
          icon={UserCheck}
          compact
          title="Chưa có yêu cầu kết nối mới"
          description="Hoàn tất xác minh bằng cấp (KYC) và khai báo lịch rảnh để hồ sơ của bạn xuất hiện khi học sinh, phụ huynh tìm gia sư."
          action={{
            label: "Xác minh bằng cấp KYC",
            href: "/tutor/verification",
            icon: ShieldCheck,
          }}
          secondaryAction={{
            label: "Cập nhật hồ sơ & Môn dạy",
            href: "/tutor/profile",
            icon: Sparkles,
          }}
        />
      </div>
    );
  }

  return (
    <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs">
      <div className="flex justify-between items-center mb-4">
        <div className="flex items-center gap-2">
          <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
            Yêu cầu kết nối đang chờ phản hồi
          </h3>
          <span className="size-5 bg-action text-on-action font-medium text-xs rounded-full flex items-center justify-center">
            {requests.length}
          </span>
        </div>
        <Link href="/tutor/requests" className="text-xs text-brand-strong font-medium hover:underline">
          Xem tất cả
        </Link>
      </div>

      <div className="space-y-3">
        {requests.map((req, idx) => (
          <div
            key={idx}
            className="p-4 rounded-2xl border border-line hover:border-action-line transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
          >
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-xs sm:text-sm font-medium text-ink">{req.name}</h4>
                <span className="px-2 py-0.5 bg-action-soft text-action-strong text-xs font-medium rounded-md border border-action-line">
                  {req.match}
                </span>
              </div>
              <p className="text-xs font-medium text-ink-2 mt-0.5">{req.subject}</p>
              <p className="text-xs text-muted mt-1 italic font-sans">
                {req.note ? `"${req.note}"` : "Không kèm lời nhắn"}
              </p>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                className="px-3 py-1.5 border border-line hover:bg-paper text-ink-2 rounded-xl text-xs font-medium transition-colors cursor-pointer"
              >
                Chi tiết
              </button>
              <button
                type="button"
                className="px-3 py-1.5 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium shadow-2xs transition-colors cursor-pointer"
              >
                Chấp nhận
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
