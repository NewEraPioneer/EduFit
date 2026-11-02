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
    <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs space-y-3 transition-colors">
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-purple-700 dark:text-purple-400" />
          <h4 className="text-xs font-bold text-neutral-900 dark:text-stone-100 uppercase tracking-wider">
            Hàng đợi KYC Gia sư
          </h4>
        </div>
        {isAllClear ? (
          <span className="px-2 py-0.5 bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 font-bold text-[10px] rounded-full flex items-center gap-1 border border-emerald-200 dark:border-emerald-800/60">
            <CheckCircle2 className="size-3" /> Hoàn tất
          </span>
        ) : (
          <span className="size-5 bg-red-500 text-white font-bold text-[10px] rounded-full flex items-center justify-center">
            {count}
          </span>
        )}
      </div>

      <p className="text-xs text-neutral-500 dark:text-stone-400 leading-relaxed font-sans">
        {isAllClear
          ? "Hiện không có hồ sơ gia sư nào tồn đọng. Mọi chứng chỉ và văn bằng đã được thẩm định."
          : `Hiện có ${count} hồ sơ gia sư mới nộp giấy tờ (bằng tốt nghiệp, chứng chỉ IELTS) đang chờ phê duyệt.`}
      </p>

      <Link
        href="/admin/verification-queue"
        className="w-full py-2.5 px-3 bg-purple-50 dark:bg-purple-950/40 hover:bg-purple-100 dark:hover:bg-purple-900/50 text-purple-900 dark:text-purple-300 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
      >
        <span>{isAllClear ? "Mở hàng đợi kiểm duyệt" : "Vào duyệt hồ sơ ngay"}</span>
        <ArrowRight className="size-3.5" />
      </Link>
    </div>
  );
}
