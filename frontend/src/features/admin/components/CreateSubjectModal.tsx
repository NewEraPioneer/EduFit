"use client";

import React, { useState } from "react";
import { X, BookPlus, Loader2, AlertCircle } from "lucide-react";
import { adminApi } from "../api/adminApi";
import { createSubjectSchema } from "../schemas/adminSchemas";
import type { SubjectAdmin } from "../types";

interface CreateSubjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (subject: SubjectAdmin) => void;
}

export function CreateSubjectModal({ isOpen, onClose, onSuccess }: CreateSubjectModalProps) {
  const [name, setName] = useState("");
  const [category, setCategory] = useState("Khoa học Tự nhiên");
  const [code, setCode] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");

  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormErrors({});
    setApiError(null);

    const validation = createSubjectSchema.safeParse({
      name,
      category,
      code,
      slug,
      description,
    });

    if (!validation.success) {
      const errors: Record<string, string> = {};
      validation.error.issues.forEach((issue) => {
        const fieldName = issue.path[0] as string;
        if (fieldName && !errors[fieldName]) {
          errors[fieldName] = issue.message;
        }
      });
      setFormErrors(errors);
      return;
    }

    setIsSubmitting(true);
    try {
      const created = await adminApi.createSubject({
        name: validation.data.name.trim(),
        category: validation.data.category?.trim() || undefined,
        code: validation.data.code?.trim() || undefined,
        slug: validation.data.slug?.trim() || undefined,
        description: validation.data.description?.trim() || undefined,
      });
      onSuccess(created);
      onClose();
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Đã xảy ra lỗi khi tạo môn học. Vui lòng kiểm tra lại.";
      setApiError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto"
    >
      <div className="bg-surface w-full max-w-dialog rounded-3xl border border-line shadow-xl overflow-hidden transition-colors my-auto">
        {/* Header */}
        <div className="px-6 py-5 border-b border-line flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-2xl bg-brand-soft text-brand-strong flex items-center justify-center">
              <BookPlus className="size-5" />
            </div>
            <div>
              <h3 id="modal-title" className="font-heading text-lg font-bold text-ink">
                Thêm môn học mới
              </h3>
              <p className="text-xs text-muted">
                Tạo môn học cho hệ thống tuyển dụng & tìm kiếm gia sư
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="size-8 rounded-full flex items-center justify-center text-muted hover:text-ink-2 hover:bg-sunken transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {apiError && (
            <div className="p-3.5 rounded-2xl bg-danger-soft border border-danger-line text-danger-strong text-xs flex items-center gap-2">
              <AlertCircle className="size-4 shrink-0" />
              <span>{apiError}</span>
            </div>
          )}

          {/* Tên môn học */}
          <div>
            <label className="block text-xs font-medium text-ink mb-1">
              Tên môn học <span className="text-danger-strong">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="VD: Toán 12 Nâng Cao, IELTS Foundation..."
              disabled={isSubmitting}
              className={`w-full px-3.5 py-2.5 rounded-xl border text-xs text-ink bg-surface outline-none transition-all ${
                formErrors.name
                  ? "border-danger focus:ring-2 focus:ring-danger/20"
                  : "border-line focus:border-brand focus:ring-2 focus:ring-brand/20"
              }`}
            />
            {formErrors.name && (
              <p className="text-xs text-danger-strong mt-1 font-medium">{formErrors.name}</p>
            )}
          </div>

          {/* Danh mục */}
          <div>
            <label className="block text-xs font-medium text-ink mb-1">
              Phân loại danh mục
            </label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2.5 rounded-xl border border-line text-xs text-ink bg-surface outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 transition-all"
            >
              <option value="Khoa học Tự nhiên">Khoa học Tự nhiên (Toán, Lý, Hóa, Sinh)</option>
              <option value="Khoa học Xã hội">Khoa học Xã hội (Văn, Sử, Địa, GDCD)</option>
              <option value="Ngoại ngữ">Ngoại ngữ (Tiếng Anh, Tiếng Trung, Nhật, Hàn)</option>
              <option value="Tin học & Lập trình">Tin học và lập trình (Tin THCS/THPT, Coding)</option>
              <option value="Nghệ thuật & Kỹ năng">Nghệ thuật và kỹ năng sống</option>
              <option value="Khác">Danh mục khác</option>
            </select>
          </div>

          {/* Mã môn học & Slug */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-ink mb-1">
                Mã môn học (Code)
              </label>
              <input
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="VD: MATH_12 (tùy chọn)"
                disabled={isSubmitting}
                className="w-full px-3.5 py-2.5 rounded-xl border border-line text-xs text-ink bg-surface outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 transition-all"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink mb-1">
                Đường dẫn tĩnh (Slug)
              </label>
              <input
                type="text"
                value={slug}
                onChange={(e) => setSlug(e.target.value.toLowerCase())}
                placeholder="VD: toan-12 (tự động tạo nếu trống)"
                disabled={isSubmitting}
                className="w-full px-3.5 py-2.5 rounded-xl border border-line text-xs text-ink bg-surface outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 transition-all"
              />
            </div>
          </div>

          {/* Mô tả */}
          <div>
            <label className="block text-xs font-medium text-ink mb-1">
              Mô tả ngắn gọn
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="VD: Chương trình ôn thi tốt nghiệp THPT và luyện đề vào đại học..."
              disabled={isSubmitting}
              className="w-full px-3.5 py-2 rounded-xl border border-line text-xs text-ink bg-surface outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 transition-all resize-none"
            />
          </div>

          {/* Actions */}
          <div className="pt-3 border-t border-line flex items-center justify-end gap-2.5">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2.5 rounded-xl border border-line text-xs font-medium text-ink-2 hover:bg-sunken transition-colors cursor-pointer"
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2.5 rounded-xl bg-brand hover:bg-brand-deep text-white text-xs font-medium shadow-xs transition-colors inline-flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  <span>Đang lưu...</span>
                </>
              ) : (
                <span>Tạo môn học</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
