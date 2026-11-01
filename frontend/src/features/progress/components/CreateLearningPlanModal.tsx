"use client";

import React, { useState, useEffect } from "react";
import {
  X,
  Plus,
  Trash2,
  Calendar,
  Award,
  Loader2,
  AlertCircle,
  HelpCircle,
  Star,
  CheckCircle2,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import { createPlanSchema, CreatePlanInput } from "../schemas/progressSchemas";
import type { PlanResponse } from "../types";
import { calculateWeightedProgress } from "../utils/progressCalculator";
import { formatLocalDateToIso } from "@/shared/utils/dateTime";

export interface CreateLearningPlanModalProps {
  isOpen: boolean;
  onClose: () => void;
  classId: string;
  className?: string;
  studentName?: string;
  initialPlan?: PlanResponse | null;
  onSuccess?: (plan: PlanResponse) => void;
}

interface MilestoneFormField {
  milestoneId?: string;
  title: string;
  description: string;
  targetDate: string;
  weight: number;
}

export function CreateLearningPlanModal({
  isOpen,
  onClose,
  classId,
  className,
  studentName,
  initialPlan,
  onSuccess,
}: CreateLearningPlanModalProps) {
  const [summary, setSummary] = useState("");
  const [milestones, setMilestones] = useState<MilestoneFormField[]>([
    { title: "", description: "", targetDate: "", weight: 1 },
  ]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Reset or fill form on open / initialPlan change
  useEffect(() => {
    if (isOpen) {
      setErrorMessage(null);
      setFieldErrors({});
      if (initialPlan) {
        setSummary(initialPlan.summary || "");
        if (initialPlan.milestones && initialPlan.milestones.length > 0) {
          setMilestones(
            initialPlan.milestones.map((m) => ({
              milestoneId: m.milestoneId,
              title: m.title,
              description: m.description || "",
              targetDate: m.targetDate ? formatLocalDateToIso(m.targetDate) : "",
              weight: m.weight || 1,
            }))
          );
        }
      } else {
        setSummary("");
        setMilestones([{ title: "", description: "", targetDate: "", weight: 1 }]);
      }
    }
  }, [isOpen, initialPlan]);

  if (!isOpen) return null;

  const isEditing = Boolean(initialPlan && initialPlan.planId);

  // Milestone handlers
  const handleAddMilestone = () => {
    if (milestones.length >= 20) {
      setErrorMessage("Kế hoạch học tập chỉ có thể có tối đa 20 cột mốc.");
      return;
    }
    setMilestones([
      ...milestones,
      { title: "", description: "", targetDate: "", weight: 1 },
    ]);
  };

  const handleRemoveMilestone = (index: number) => {
    if (milestones.length <= 1) {
      setErrorMessage("Kế hoạch học tập phải có ít nhất 1 cột mốc.");
      return;
    }
    setMilestones(milestones.filter((_, idx) => idx !== index));
  };

  const handleMilestoneChange = (
    index: number,
    field: keyof MilestoneFormField,
    value: string | number
  ) => {
    const updated = [...milestones];
    updated[index] = { ...updated[index], [field]: value };
    setMilestones(updated);
  };

  // Preview stats
  const totalWeight = milestones.reduce((sum, m) => sum + (Number(m.weight) || 1), 0);

  // Submit
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setFieldErrors({});

    const payload: CreatePlanInput = {
      classId,
      summary: summary.trim() || undefined,
      milestones: milestones.map((m) => ({
        title: m.title.trim(),
        description: m.description.trim() || undefined,
        targetDate: m.targetDate || undefined,
        weight: Number(m.weight) || 1,
      })),
    };

    const validation = createPlanSchema.safeParse(payload);
    if (!validation.success) {
      const errors: Record<string, string> = {};
      validation.error.issues.forEach((issue) => {
        const path = issue.path.join(".");
        errors[path] = issue.message;
      });
      setFieldErrors(errors);
      setErrorMessage(
        validation.error.issues[0]?.message || "Dữ liệu kế hoạch học tập không hợp lệ."
      );
      return;
    }

    setLoading(true);
    try {
      if (isEditing && initialPlan) {
        const res = await progressApi.updatePlan(initialPlan.planId, {
          version: initialPlan.version,
          summary: payload.summary,
          milestones: milestones.map((m) => ({
            milestoneId: m.milestoneId,
            title: m.title.trim(),
            description: m.description.trim() || undefined,
            targetDate: m.targetDate || undefined,
            weight: Number(m.weight) || 1,
          })),
        });
        if (res.success && res.data) {
          onSuccess?.(res.data);
          onClose();
        } else {
          setErrorMessage(res.message || "Không thể cập nhật kế hoạch học tập.");
        }
      } else {
        const res = await progressApi.createPlan(payload);
        if (res.success && res.data) {
          onSuccess?.(res.data);
          onClose();
        } else {
          setErrorMessage(res.message || "Không thể tạo kế hoạch học tập.");
        }
      }
    } catch (err: unknown) {
      setErrorMessage(
        err instanceof Error
          ? err.message
          : "Đã xảy ra lỗi khi lưu kế hoạch học tập. Vui lòng thử lại."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="plan-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs overflow-y-auto"
    >
      <div className="relative w-full max-w-3xl overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 animate-in fade-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-stone-100 px-6 py-4 bg-stone-50/50">
          <div className="flex items-center gap-3">
            <div className="grid size-10 place-items-center rounded-2xl bg-amber-100 text-amber-800">
              <Award className="size-5" />
            </div>
            <div>
              <h2 id="plan-modal-title" className="text-base font-bold text-neutral-900">
                {isEditing ? "Cập nhật Kế hoạch học tập" : "Thiết lập Kế hoạch học tập & Cột mốc"}
              </h2>
              <p className="text-xs text-neutral-500 mt-0.5">
                {className ? `${className}` : "Lớp học kèm 1:1"}{" "}
                {studentName ? `• Học sinh: ${studentName}` : ""}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 hover:text-neutral-700 transition-colors"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Modal Form */}
        <form onSubmit={handleSubmit} className="flex flex-col max-h-[80vh]">
          <div className="p-6 space-y-6 overflow-y-auto">
            {/* Error Alert */}
            {errorMessage && (
              <div
                role="alert"
                className="flex items-start gap-3 rounded-2xl bg-rose-50 border border-rose-200 p-4 text-xs text-rose-800 font-medium"
              >
                <AlertCircle className="size-4.5 text-rose-600 shrink-0 mt-0.5" />
                <div>
                  <p className="font-bold">Không thể lưu kế hoạch</p>
                  <p className="mt-0.5">{errorMessage}</p>
                </div>
              </div>
            )}

            {/* Summary */}
            <div className="space-y-1.5">
              <label
                htmlFor="plan-summary"
                className="block text-xs font-bold text-neutral-800 uppercase tracking-wider"
              >
                Mục tiêu chung & Tóm tắt kế hoạch
              </label>
              <textarea
                id="plan-summary"
                rows={3}
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="Ví dụ: Ôn tập lấy lại căn bản môn Toán lớp 9, làm quen các dạng bài tập hình học và phương trình bậc hai hướng tới kỳ thi vào 10..."
                className="w-full rounded-2xl border border-stone-200 bg-stone-50/50 p-3 text-xs text-neutral-900 placeholder:text-neutral-400 focus:bg-white focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 focus:outline-hidden transition-all"
                maxLength={1000}
              />
              <div className="flex justify-between text-[11px] text-neutral-400 px-1">
                <span>Tóm tắt tổng quát giúp học sinh và phụ huynh nắm rõ lộ trình</span>
                <span>{summary.length}/1000</span>
              </div>
            </div>

            {/* Milestones Header & Quick Stats */}
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-stone-100 pb-2">
                <div className="flex items-center gap-2">
                  <h3 className="text-xs font-bold text-neutral-800 uppercase tracking-wider">
                    Danh sách các cột mốc kiến thức (1 - 20 mốc)
                  </h3>
                  <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-bold text-amber-700 border border-amber-200">
                    {milestones.length} mốc • Tổng trọng số: {totalWeight}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={handleAddMilestone}
                  disabled={milestones.length >= 20}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-amber-500 px-3 py-1.5 text-xs font-bold text-white shadow-xs hover:bg-amber-600 active:scale-95 disabled:opacity-50 transition-all cursor-pointer"
                >
                  <Plus className="size-3.5" />
                  Thêm cột mốc
                </button>
              </div>

              {/* Formula Explainer */}
              <div className="flex items-center gap-2 rounded-xl bg-stone-50 p-3 text-[11px] text-neutral-600 border border-stone-200/60">
                <HelpCircle className="size-4 text-amber-600 shrink-0" />
                <span>
                  <strong>Quy tắc trọng số:</strong> Mỗi cột mốc có trọng số từ 1 đến 5 (1 = Nhẹ, 5 = Trọng tâm cốt lõi).
                  Tiến độ tổng thể được tính bằng tổng trọng số các mốc đã đạt chia cho tổng trọng số toàn kế hoạch.
                </span>
              </div>

              {/* Milestones List */}
              <div className="space-y-4">
                {milestones.map((item, idx) => {
                  const titleError = fieldErrors[`milestones.${idx}.title`];
                  return (
                    <div
                      key={idx}
                      className="relative rounded-2xl border border-stone-200 bg-white p-4 shadow-xs space-y-3 transition-shadow hover:shadow-md"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="grid size-6 place-items-center rounded-lg bg-stone-100 text-xs font-bold text-neutral-700">
                            {idx + 1}
                          </span>
                          <span className="text-xs font-bold text-neutral-800">
                            Cột mốc {idx + 1}
                          </span>
                        </div>

                        {milestones.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemoveMilestone(idx)}
                            className="rounded-lg p-1 text-neutral-400 hover:bg-rose-50 hover:text-rose-600 transition-colors cursor-pointer"
                            title="Xóa cột mốc này"
                            aria-label={`Xóa cột mốc ${idx + 1}`}
                          >
                            <Trash2 className="size-4" />
                          </button>
                        )}
                      </div>

                      {/* Title input */}
                      <div>
                        <label className="block text-[11px] font-bold text-neutral-700 mb-1">
                          Tiêu đề cột mốc <span className="text-rose-500">*</span>
                        </label>
                        <input
                          type="text"
                          value={item.title}
                          onChange={(e) => handleMilestoneChange(idx, "title", e.target.value)}
                          placeholder="Ví dụ: Nắm vững Định lý Vi-ét và ứng dụng giải bài toán"
                          className={`w-full rounded-xl border px-3 py-2 text-xs text-neutral-900 placeholder:text-neutral-400 focus:outline-hidden ${
                            titleError
                              ? "border-rose-300 bg-rose-50/30 focus:border-rose-500"
                              : "border-stone-200 bg-stone-50/40 focus:bg-white focus:border-amber-500 focus:ring-1 focus:ring-amber-500"
                          }`}
                          maxLength={200}
                        />
                        {titleError && (
                          <p className="text-[10px] text-rose-600 mt-1">{titleError}</p>
                        )}
                      </div>

                      {/* Description & Date & Weight Row */}
                      <div className="grid grid-cols-1 sm:grid-cols-12 gap-3">
                        <div className="sm:col-span-6">
                          <label className="block text-[11px] font-bold text-neutral-700 mb-1">
                            Mô tả chi tiết (Tùy chọn)
                          </label>
                          <input
                            type="text"
                            value={item.description}
                            onChange={(e) =>
                              handleMilestoneChange(idx, "description", e.target.value)
                            }
                            placeholder="Chi tiết bài tập, kỹ năng cần thành thạo..."
                            className="w-full rounded-xl border border-stone-200 bg-stone-50/40 px-3 py-2 text-xs text-neutral-900 placeholder:text-neutral-400 focus:bg-white focus:border-amber-500 focus:outline-hidden"
                            maxLength={1000}
                          />
                        </div>

                        <div className="sm:col-span-3">
                          <label className="block text-[11px] font-bold text-neutral-700 mb-1">
                            Hạn dự kiến
                          </label>
                          <div className="relative">
                            <input
                              type="date"
                              value={item.targetDate}
                              onChange={(e) =>
                                handleMilestoneChange(idx, "targetDate", e.target.value)
                              }
                              className="w-full rounded-xl border border-stone-200 bg-stone-50/40 px-3 py-2 text-xs text-neutral-900 focus:bg-white focus:border-amber-500 focus:outline-hidden"
                            />
                          </div>
                        </div>

                        <div className="sm:col-span-3">
                          <label className="block text-[11px] font-bold text-neutral-700 mb-1">
                            Trọng số (1 - 5)
                          </label>
                          <div className="flex items-center gap-1">
                            {[1, 2, 3, 4, 5].map((w) => {
                              const isSelected = item.weight === w;
                              return (
                                <button
                                  key={w}
                                  type="button"
                                  onClick={() => handleMilestoneChange(idx, "weight", w)}
                                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                                    isSelected
                                      ? "bg-amber-500 text-white shadow-xs"
                                      : "bg-stone-100 text-neutral-600 hover:bg-stone-200"
                                  }`}
                                  title={`Trọng số ${w}/5`}
                                >
                                  {w}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t border-stone-100 px-6 py-4 bg-stone-50/50">
            <span className="text-xs text-neutral-500">
              {milestones.length} cột mốc sẽ được tạo
            </span>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="h-9 px-4 rounded-xl border border-stone-200 bg-white text-xs font-bold text-neutral-700 hover:bg-stone-50 active:scale-95 transition-all cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="submit"
                disabled={loading}
                className="h-9 px-5 rounded-xl bg-amber-500 hover:bg-amber-600 active:scale-95 text-white text-xs font-bold shadow-xs flex items-center gap-1.5 cursor-pointer disabled:opacity-50 transition-all"
              >
                {loading ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <CheckCircle2 className="size-3.5" />
                )}
                {isEditing ? "Lưu thay đổi" : "Tạo kế hoạch"}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
