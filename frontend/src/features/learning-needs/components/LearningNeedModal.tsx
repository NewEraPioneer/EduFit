"use client";

import React from "react";
import { createPortal } from "react-dom";
import { Loader2, Target, X } from "lucide-react";
import type { LearningNeed, SaveLearningNeedPayload } from "@/services/learningNeeds";
import { useLearningCatalog } from "../hooks/useLearningCatalog";
import { LearningNeedForm } from "./LearningNeedForm";

export interface LearningNeedModalProps {
  isOpen: boolean;
  title?: string;
  description?: string;
  need?: LearningNeed | null;
  defaults?: Partial<SaveLearningNeedPayload>;
  studentId?: string;
  submitLabel?: string;
  onClose: () => void;
  onSaved: (need: LearningNeed) => void | Promise<void>;
}

/**
 * Hộp thoại tạo/sửa một nhu cầu học (đầy đủ điều kiện học). Render qua portal vì có thể được mở
 * từ bên trong một form khác (hồ sơ phụ huynh, hộp thoại gửi yêu cầu).
 */
export function LearningNeedModal({
  isOpen,
  title,
  description,
  need,
  defaults,
  studentId,
  submitLabel,
  onClose,
  onSaved,
}: LearningNeedModalProps) {
  const { catalog, error } = useLearningCatalog();
  if (!isOpen) return null;

  return createPortal(
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-4 bg-neutral-950/50 backdrop-blur-xs overflow-y-auto">
      <div role="dialog" aria-modal="true" aria-labelledby="learning-need-title"
        className="bg-white dark:bg-stone-900 rounded-3xl max-w-xl w-full border border-stone-200 dark:border-stone-700 shadow-2xl my-auto">
        <div className="px-6 py-5 border-b border-stone-100 dark:border-stone-800 flex items-start justify-between gap-4">
          <div className="flex items-start gap-2.5 min-w-0">
            <div className="size-9 shrink-0 rounded-2xl bg-sky-50 dark:bg-sky-950/60 text-sky-600 dark:text-sky-400 flex items-center justify-center">
              <Target className="size-5" />
            </div>
            <div className="min-w-0">
              <h3 id="learning-need-title" className="font-heading font-bold text-base text-neutral-900 dark:text-stone-100">
                {title ?? (need ? "Chỉnh sửa nhu cầu học" : "Thêm nhu cầu học")}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-stone-400">
                {description ?? "Chọn theo thứ tự Lớp → Môn → Mục tiêu. Hệ thống chỉ hiện lựa chọn phù hợp."}
              </p>
            </div>
          </div>
          <button type="button" aria-label="Đóng" onClick={onClose}
            className="p-2 text-neutral-400 hover:text-neutral-700 dark:hover:text-stone-200 hover:bg-stone-100 dark:hover:bg-stone-800 rounded-full transition-colors">
            <X className="size-4" />
          </button>
        </div>
        <div className="p-6">
          {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
          {!catalog && !error && (
            <div className="flex items-center gap-2 text-sm text-neutral-500"><Loader2 className="size-4 animate-spin" />Đang tải danh mục…</div>
          )}
          {catalog && (
            <LearningNeedForm
              catalog={catalog}
              need={need}
              defaults={defaults}
              studentId={studentId}
              submitLabel={submitLabel}
              onCancel={onClose}
              onSaved={onSaved}
            />
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
