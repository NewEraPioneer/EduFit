"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { ShieldCheck, ArrowRight, CheckCircle2 } from "lucide-react";
import { verificationService } from "@/services/verification";

interface AdminKycQueueCardProps {
  pendingCount?: number;
}

export function AdminKycQueueCard({ pendingCount }: AdminKycQueueCardProps) {
  const [count, setCount] = useState<number>(pendingCount ?? 0);

  useEffect(() => {
    if (pendingCount !== undefined) {
      setCount(pendingCount);
      return;
    }
    let ignore = false;
    verificationService
      .getPendingQueue(0, 1)
      .then((res) => {
        if (!ignore && res && typeof res.totalElements === "number") {
          setCount(res.totalElements);
        }
      })
      .catch(() => {
        // keep fallback
      });

    return () => {
      ignore = true;
    };
  }, [pendingCount]);

  const isAllClear = count === 0;

  return (
    <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-xs space-y-3 transition-colors">
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-brand-strong" />
          <h4 className="text-xs font-medium text-ink">
            Hàng đợi KYC Gia sư
          </h4>
        </div>
        {isAllClear ? (
          <span className="px-2 py-0.5 bg-success-soft text-success-strong font-medium text-xs rounded-full flex items-center gap-1 border border-success-line">
            <CheckCircle2 className="size-3" /> Hoàn tất
          </span>
        ) : (
          <span className="size-5 bg-danger text-white font-medium text-xs rounded-full flex items-center justify-center">
            {count}
          </span>
        )}
      </div>

      <p className="text-xs text-muted leading-relaxed font-sans">
        {isAllClear
          ? "Hiện không có hồ sơ gia sư nào tồn đọng. Mọi chứng chỉ và văn bằng đã được thẩm định."
          : `Hiện có ${count} hồ sơ gia sư mới nộp giấy tờ (bằng tốt nghiệp, chứng chỉ IELTS) đang chờ phê duyệt.`}
      </p>

      <Link
        href="/admin/verification-queue"
        className="w-full py-2.5 px-3 bg-brand-soft hover:bg-brand-soft text-brand-strong rounded-xl text-xs font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
      >
        <span>{isAllClear ? "Mở hàng đợi kiểm duyệt" : "Vào duyệt hồ sơ ngay"}</span>
        <ArrowRight className="size-3.5" />
      </Link>
    </div>
  );
}
