"use client";

import React from "react";
import Link from "next/link";
import { MessageSquare, Sparkles, GraduationCap, Search } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

interface ActiveTutorItem {
  name: string;
  subject: string;
  rating: string;
  recentNote: string;
  progress: string;
}

interface ParentActiveTutorsProps {
  tutors?: ActiveTutorItem[];
}

export function ParentActiveTutors({ tutors = [] }: ParentActiveTutorsProps) {
  if (tutors.length === 0) {
    return (
      <div className="bg-white p-6 rounded-3xl border border-stone-200/80 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900">
            Gia sư trực tiếp đồng hành
          </h3>
          <span className="text-xs text-neutral-400 font-medium">0 gia sư</span>
        </div>

        <EmptyState
          icon={GraduationCap}
          theme="emerald"
          compact
          title="Chưa có gia sư nào đang dạy kèm"
          description="Khám phá mạng lưới gia sư sư phạm chuyên môn cao, đã được EduFit xác minh chứng chỉ và lý lịch rõ ràng."
          action={{
            label: "Tìm kiếm gia sư cho con",
            href: "/parent/tutors",
            icon: Search,
          }}
          secondaryAction={{
            label: "Xem gợi ý AI Match",
            href: "/parent/tutors?tab=matches",
            icon: Sparkles,
          }}
        />
      </div>
    );
  }

  return (
    <div className="bg-white p-6 rounded-3xl border border-stone-200/80 shadow-xs">
      <div className="flex justify-between items-center mb-4">
        <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900">
          Gia sư trực tiếp đồng hành
        </h3>
        <Link href="/parent/tutors" className="text-xs text-emerald-700 font-bold hover:underline">
          Tìm thêm gia sư
        </Link>
      </div>

      <div className="space-y-4">
        {tutors.map((tutor, idx) => (
          <div
            key={idx}
            className="p-4 rounded-2xl border border-stone-200/80 hover:border-emerald-300 transition-colors space-y-3"
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div className="flex items-center gap-3">
                <div className="size-10 rounded-xl bg-emerald-100 text-emerald-800 font-bold text-sm flex items-center justify-center shrink-0">
                  {tutor.name.charAt(0)}
                </div>
                <div>
                  <h4 className="text-xs sm:text-sm font-bold text-neutral-900">{tutor.name}</h4>
                  <div className="flex items-center gap-2 text-xs text-neutral-500">
                    <span className="font-semibold text-neutral-700">{tutor.subject}</span>
                    <span>·</span>
                    <span className="text-amber-600 font-medium">{tutor.rating}</span>
                    {tutor.progress && (
                      <>
                        <span>·</span>
                        <span className="font-medium text-emerald-700">{tutor.progress}</span>
                      </>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto">
                <button
                  type="button"
                  disabled
                  className="px-3 py-1.5 border border-stone-200 text-neutral-500 rounded-xl text-xs font-semibold flex items-center gap-1.5 cursor-not-allowed"
                >
                  <MessageSquare className="size-3.5 text-neutral-500" />
                  <span>Nhắn tin · Sắp ra mắt</span>
                </button>
              </div>
            </div>

            <div className="p-3 bg-stone-50 rounded-xl border border-stone-200/60 text-xs text-neutral-600 space-y-1">
              <span className="text-[10px] uppercase font-bold text-neutral-400 tracking-wider">
                Nhận xét gần nhất từ gia sư
              </span>
              <p className="italic font-sans">{`"${tutor.recentNote}"`}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
