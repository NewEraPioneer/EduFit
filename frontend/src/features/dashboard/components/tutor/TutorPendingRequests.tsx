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
      <div className="bg-white p-6 rounded-3xl border border-stone-200/80 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900">
              Yêu cầu kết nối đang chờ phản hồi
            </h3>
            <span className="size-5 bg-stone-200 text-neutral-600 font-bold text-[10px] rounded-full flex items-center justify-center">
              0
            </span>
          </div>
          <span className="text-xs text-neutral-400 font-medium">Hàng đợi</span>
        </div>

        <EmptyState
          icon={UserCheck}
          theme="sky"
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
    <div className="bg-white p-6 rounded-3xl border border-stone-200/80 shadow-xs">
      <div className="flex justify-between items-center mb-4">
        <div className="flex items-center gap-2">
          <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900">
            Yêu cầu kết nối đang chờ phản hồi
          </h3>
          <span className="size-5 bg-amber-400 text-neutral-950 font-bold text-[10px] rounded-full flex items-center justify-center">
            {requests.length}
          </span>
        </div>
        <Link href="/tutor/requests" className="text-xs text-sky-600 font-bold hover:underline">
          Xem tất cả
        </Link>
      </div>

      <div className="space-y-3">
        {requests.map((req, idx) => (
          <div
            key={idx}
            className="p-4 rounded-2xl border border-stone-200 hover:border-amber-300 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
          >
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-xs sm:text-sm font-bold text-neutral-900">{req.name}</h4>
                <span className="px-2 py-0.5 bg-amber-50 text-amber-900 text-[10px] font-bold rounded-md border border-amber-200">
                  {req.match}
                </span>
              </div>
              <p className="text-xs font-semibold text-neutral-700 mt-0.5">{req.subject}</p>
              <p className="text-xs text-neutral-500 mt-1 italic font-sans">
                {req.note ? `"${req.note}"` : "Không kèm lời nhắn"}
              </p>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                className="px-3 py-1.5 border border-stone-200 hover:bg-stone-50 text-neutral-700 rounded-xl text-xs font-bold transition-colors cursor-pointer"
              >
                Chi tiết
              </button>
              <button
                type="button"
                className="px-3 py-1.5 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-xs font-bold shadow-2xs transition-colors cursor-pointer"
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
