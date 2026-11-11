"use client";

import React from "react";
import { X, UploadCloud, FileText, CheckCircle2, Trash2 } from "lucide-react";
import { useKycUpload } from "../hooks/useKycUpload";
import type { CredentialType } from "../types";

interface UploadCredentialModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function UploadCredentialModal({ isOpen, onClose, onSuccess }: UploadCredentialModalProps) {
  const {
    institutionName,
    setInstitutionName,
    graduationYear,
    setGraduationYear,
    documentType,
    setDocumentType,
    handleDocumentTypeChange,
    notes,
    setNotes,
    selectedFiles,
    isSubmitting,
    error,
    handleFileChange,
    removeFile,
    resetForm,
    submit,
  } = useKycUpload();

  if (!isOpen) return null;

  const handleClose = () => {
    if (isSubmitting) return;
    resetForm();
    onClose();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    void submit(onSuccess, onClose);
  };

  return (
    <div className="fixed inset-0 z-50 bg-night/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-4 overflow-y-auto">
      <div className="bg-surface rounded-3xl max-w-dialog w-full p-6 sm:p-7 shadow-xl space-y-5 animate-in fade-in zoom-in-95 duration-150 my-auto">
        <div className="flex items-center justify-between pb-3 border-b border-line">
          <div>
            <h3 className="font-heading text-lg font-bold text-ink">
              Nộp hồ sơ xác minh gia sư (KYC)
            </h3>
            <p className="text-xs text-muted mt-0.5">
              Hồ sơ của bạn sẽ được bảo mật và chỉ dùng cho mục đích xác thực danh tính.
            </p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            disabled={isSubmitting}
            className="p-1.5 rounded-xl hover:bg-sunken text-muted hover:text-ink-2 transition-colors"
            aria-label="Đóng hộp thoại"
          >
            <X className="size-5" />
          </button>
        </div>

        {error && (
          <div className="p-3 bg-danger-soft border border-danger-line text-danger-strong rounded-xl text-xs font-medium">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1">Loại giấy tờ xác minh</label>
            <select
              value={documentType}
              onChange={(e) => handleDocumentTypeChange(e.target.value as CredentialType)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-line bg-paper text-xs font-medium focus:bg-surface focus:outline-brand"
            >
              <option value="DEGREE">Bằng cấp</option>
              <option value="CERTIFICATE">Chứng chỉ</option>
              <option value="TEACHING_LICENCE">Chứng chỉ sư phạm</option>
              <option value="STUDENT_CARD">Thẻ sinh viên</option>
              <option value="OTHER">Giấy tờ khác / CCCD</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1">Trường đại học / Tổ chức cấp</label>
            <input
              type="text"
              value={institutionName}
              onChange={(e) => setInstitutionName(e.target.value)}
              placeholder="Ví dụ: Đại học Sư phạm Hà Nội"
              className="w-full px-3.5 py-2.5 rounded-xl border border-line bg-paper text-xs font-medium focus:bg-surface focus:outline-brand"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-ink-2 mb-1">
                Năm tốt nghiệp (hoặc dự kiến){documentType === "OTHER" ? " - Không bắt buộc" : ""}
              </label>
              <input
                type="number"
                value={graduationYear ?? ""}
                onChange={(e) =>
                  setGraduationYear(e.target.value ? Number(e.target.value) : undefined)
                }
                placeholder={documentType === "OTHER" ? "Để trống nếu là CCCD" : "Ví dụ: 2024"}
                className="w-full px-3.5 py-2.5 rounded-xl border border-line bg-paper text-xs font-medium focus:bg-surface focus:outline-brand"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-2 mb-1">Ghi chú thêm</label>
              <input
                type="text"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Tùy chọn..."
                className="w-full px-3.5 py-2.5 rounded-xl border border-line bg-paper text-xs font-medium focus:bg-surface focus:outline-brand"
              />
            </div>
          </div>

          {/* File Upload Box */}
          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1">Tải ảnh bằng cấp / chứng chỉ</label>
            <label className="border-2 border-dashed border-line hover:border-brand rounded-2xl p-4 flex flex-col items-center justify-center cursor-pointer transition-colors bg-paper/50">
              <UploadCloud className="size-6 text-brand-strong mb-1" />
              <span className="text-xs font-medium text-ink-2">Chọn tệp (JPG, PNG, PDF)</span>
              <span className="text-xs text-muted mt-0.5">Tối đa 10MB/tệp</span>
              <input type="file" onChange={handleFileChange} multiple accept=".pdf,.jpg,.jpeg,.png" className="hidden" />
            </label>
            {selectedFiles.length > 0 && (
              <div className="mt-2 space-y-1">
                {selectedFiles.map((f, i) => (
                  <div key={`${f.name}-${f.size}-${f.lastModified}`} className="text-xs text-ink-2 flex items-center gap-1.5 bg-sunken px-2.5 py-1 rounded-lg">
                    <FileText className="size-3 text-brand-strong" />
                    <span className="min-w-0 flex-1 truncate">{f.name}</span>
                    <button
                      type="button"
                      onClick={() => removeFile(i)}
                      className="rounded p-1 text-muted hover:bg-danger-soft hover:text-danger-strong"
                      aria-label={`Xóa tệp ${f.name}`}
                    >
                      <Trash2 className="size-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              type="button"
              onClick={handleClose}
              disabled={isSubmitting}
              className="px-4 py-2 rounded-xl border border-line hover:bg-paper text-ink-2 text-xs font-medium transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium shadow-xs transition-colors flex items-center gap-1.5 disabled:opacity-50"
            >
              {isSubmitting ? (
                <span>Đang gửi hồ sơ...</span>
              ) : (
                <>
                  <CheckCircle2 className="size-4" />
                  <span>Xác nhận nộp</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
