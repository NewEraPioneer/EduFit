"use client";

import React, { useState, useEffect } from "react";
import {
  X,
  Plus,
  Trash2,
  Award,
  Loader2,
  AlertCircle,
  HelpCircle,
  CheckCircle2,
  ListChecks,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import { createPlanSchema, CreatePlanInput, planTemplateSchema } from "../schemas/progressSchemas";
import type { PlanResponse, PlanTemplate, TemplateMilestone } from "../types";
import { MAX_CRITERIA_PER_MILESTONE } from "../constants/feedbackLabels";
import {
  DEFAULT_SUGGESTED_SESSIONS,
  MAX_PLAN_MILESTONES,
  MAX_SUGGESTED_SESSIONS,
  MAX_TEMPLATE_TITLE_LENGTH,
  MIN_PLAN_MILESTONES,
  MIN_SUGGESTED_SESSIONS,
} from "../constants/roadmap";
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

interface CriterionFormField {
  criterionId?: string;
  content: string;
}

interface MilestoneFormField {
  milestoneId?: string;
  title: string;
  description: string;
  targetDate: string;
  weight: number;
  /** Chỉ dùng khi lưu thành mẫu lộ trình (lộ trình của lớp không lưu số buổi). */
  suggestedSessions: number;
  criteria: CriterionFormField[];
}

const emptyMilestone = (): MilestoneFormField => ({
  title: "",
  description: "",
  targetDate: "",
  weight: 1,
  suggestedSessions: DEFAULT_SUGGESTED_SESSIONS,
  criteria: [{ content: "" }],
});

const emptyRoadmap = (): MilestoneFormField[] => Array.from({ length: MIN_PLAN_MILESTONES }, emptyMilestone);

/** Nhân bản mẫu vào biểu mẫu: giữ nội dung, bỏ trống hạn để gia sư đặt theo lịch của lớp. */
const fromTemplate = (m: TemplateMilestone): MilestoneFormField => ({
  title: m.title,
  description: m.description ?? "",
  targetDate: "",
  weight: m.weight,
  suggestedSessions: m.suggestedSessions,
  criteria: m.criteria.map((content) => ({ content })),
});

/** Bỏ các dòng tiêu chí để trống trước khi gửi. */
const filledCriteria = (criteria: CriterionFormField[]) =>
  criteria
    .map((c) => ({ criterionId: c.criterionId, content: c.content.trim() }))
    .filter((c) => c.content.length > 0);

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
  const [milestones, setMilestones] = useState<MilestoneFormField[]>(emptyRoadmap);
  const [templates, setTemplates] = useState<PlanTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState("");
  const [saveAsTemplate, setSaveAsTemplate] = useState(false);
  const [templateTitle, setTemplateTitle] = useState("");
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
              suggestedSessions: DEFAULT_SUGGESTED_SESSIONS,
              criteria:
                m.criteria && m.criteria.length > 0
                  ? m.criteria.map((c) => ({ criterionId: c.criterionId, content: c.content }))
                  : [{ content: "" }],
            }))
          );
        }
      } else {
        setSummary("");
        setMilestones(emptyRoadmap());
      }
      setSelectedTemplateId("");
      setSaveAsTemplate(false);
      setTemplateTitle(className ?? "");
    }
  }, [isOpen, initialPlan, className]);

  // Mẫu hợp môn × khối × mục tiêu của lớp (chỉ khi lập lộ trình mới)
  useEffect(() => {
    if (!isOpen || initialPlan?.planId) return;
    let ignore = false;
    progressApi
      .listPlanTemplates(classId)
      .then((res) => {
        if (!ignore) setTemplates(res.success && res.data ? res.data : []);
      })
      .catch(() => {
        if (!ignore) setTemplates([]);
      });
    return () => {
      ignore = true;
    };
  }, [isOpen, initialPlan, classId]);

  if (!isOpen) return null;

  const isEditing = Boolean(initialPlan && initialPlan.planId);

  // Milestone handlers
  const handleAddMilestone = () => {
    if (milestones.length >= MAX_PLAN_MILESTONES) {
      setErrorMessage(`Lộ trình có tối đa ${MAX_PLAN_MILESTONES} cột mốc; độ mịn hơn nằm trong tiêu chí đạt.`);
      return;
    }
    setMilestones([...milestones, emptyMilestone()]);
  };

  const handleRemoveMilestone = (index: number) => {
    if (milestones.length <= MIN_PLAN_MILESTONES) {
      setErrorMessage(`Lộ trình cần ít nhất ${MIN_PLAN_MILESTONES} cột mốc.`);
      return;
    }
    setMilestones(milestones.filter((_, idx) => idx !== index));
  };

  const selectedTemplate = templates.find((t) => t.templateId === selectedTemplateId);

  const handleApplyTemplate = () => {
    if (!selectedTemplate) return;
    setErrorMessage(null);
    setFieldErrors({});
    setMilestones(selectedTemplate.milestones.map(fromTemplate));
    if (!summary.trim() && selectedTemplate.description) setSummary(selectedTemplate.description);
  };

  const handleDeleteTemplate = async () => {
    if (!selectedTemplate?.mine) return;
    try {
      const res = await progressApi.deletePlanTemplate(selectedTemplate.templateId);
      if (res.success) {
        setTemplates((prev) => prev.filter((t) => t.templateId !== selectedTemplate.templateId));
        setSelectedTemplateId("");
      } else {
        setErrorMessage(res.message || "Không xóa được mẫu lộ trình.");
      }
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Không xóa được mẫu lộ trình.");
    }
  };

  const templatePayload = () => ({
    classId,
    title: templateTitle.trim(),
    milestones: milestones.map((m) => ({
      title: m.title.trim(),
      description: m.description.trim() || undefined,
      weight: Number(m.weight) || 1,
      suggestedSessions: Number(m.suggestedSessions),
      criteria: filledCriteria(m.criteria).map((c) => c.content),
    })),
  });

  const handleMilestoneChange = (
    index: number,
    field: keyof MilestoneFormField,
    value: string | number
  ) => {
    const updated = [...milestones];
    updated[index] = { ...updated[index], [field]: value };
    setMilestones(updated);
  };

  const updateCriteria = (index: number, next: CriterionFormField[]) => {
    const updated = [...milestones];
    updated[index] = { ...updated[index], criteria: next };
    setMilestones(updated);
  };

  const handleCriterionChange = (mIdx: number, cIdx: number, content: string) =>
    updateCriteria(
      mIdx,
      milestones[mIdx].criteria.map((c, i) => (i === cIdx ? { ...c, content } : c))
    );

  const handleAddCriterion = (mIdx: number) => {
    if (milestones[mIdx].criteria.length >= MAX_CRITERIA_PER_MILESTONE) return;
    updateCriteria(mIdx, [...milestones[mIdx].criteria, { content: "" }]);
  };

  const handleRemoveCriterion = (mIdx: number, cIdx: number) =>
    updateCriteria(mIdx, milestones[mIdx].criteria.filter((_, i) => i !== cIdx));

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
        criteria: filledCriteria(m.criteria).map((c) => c.content),
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

    const wantsTemplate = !isEditing && saveAsTemplate;
    if (wantsTemplate) {
      const templateValidation = planTemplateSchema.safeParse(templatePayload());
      if (!templateValidation.success) {
        setErrorMessage(templateValidation.error.issues[0]?.message || "Mẫu lộ trình chưa hợp lệ.");
        return;
      }
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
            criteria: filledCriteria(m.criteria),
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
          if (wantsTemplate) {
            const saved = await progressApi.createPlanTemplate(templatePayload());
            if (!saved.success) {
              setErrorMessage(`Đã tạo lộ trình nhưng chưa lưu được mẫu: ${saved.message || "lỗi không xác định"}`);
              return;
            }
          }
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

            {!isEditing && templates.length > 0 && (
              <div className="space-y-2 rounded-2xl border border-amber-200 bg-amber-50/50 p-3" data-testid="plan-template-picker">
                <label htmlFor="plan-template" className="block text-xs font-bold text-neutral-800 uppercase tracking-wider">
                  Bắt đầu từ mẫu lộ trình
                </label>
                <div className="flex flex-wrap items-center gap-2">
                  <select
                    id="plan-template"
                    value={selectedTemplateId}
                    onChange={(e) => setSelectedTemplateId(e.target.value)}
                    className="min-h-9 flex-1 rounded-xl border border-stone-200 bg-white px-2 text-xs"
                  >
                    <option value="">— Chọn mẫu —</option>
                    {templates.map((t) => (
                      <option key={t.templateId} value={t.templateId}>
                        {t.title} {t.mine ? "(mẫu của tôi)" : t.system ? "(mẫu hệ thống)" : ""}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={handleApplyTemplate}
                    disabled={!selectedTemplate}
                    className="min-h-9 px-3 rounded-xl bg-amber-500 text-white text-xs font-bold disabled:opacity-40 cursor-pointer"
                  >
                    Áp dụng mẫu
                  </button>
                  {selectedTemplate?.mine && (
                    <button
                      type="button"
                      onClick={() => void handleDeleteTemplate()}
                      className="min-h-9 px-3 rounded-xl border border-rose-200 bg-white text-rose-700 text-xs font-bold cursor-pointer"
                    >
                      Xóa mẫu này
                    </button>
                  )}
                </div>
                <p className="text-[11px] text-neutral-500">
                  Áp dụng sẽ thay danh sách cột mốc bên dưới; bạn vẫn sửa được trước khi lưu và cần đặt hạn cho cột mốc cuối.
                </p>
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
                    Danh sách các cột mốc kiến thức ({MIN_PLAN_MILESTONES} - {MAX_PLAN_MILESTONES} mốc)
                  </h3>
                  <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-bold text-amber-700 border border-amber-200">
                    {milestones.length} mốc • Tổng trọng số: {totalWeight}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={handleAddMilestone}
                  disabled={milestones.length >= MAX_PLAN_MILESTONES}
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
                  <strong>Quy tắc:</strong> Mỗi cột mốc có trọng số 1–5 và 1–4 tiêu chí đạt (hành vi nhìn thấy được).
                  Cột mốc đã đạt tính 100%, cột mốc đang học tính theo số tiêu chí đã tick; chỉ chốt đạt khi tick đủ tiêu chí.
                  Cột mốc cuối bắt buộc có hạn; hạn các mốc giữa có thể để trống, hệ thống tự nội suy.
                </span>
              </div>

              {/* Milestones List */}
              <div className="space-y-4">
                {milestones.map((item, idx) => {
                  const titleError = fieldErrors[`milestones.${idx}.title`];
                  const criteriaError = fieldErrors[`milestones.${idx}.criteria`];
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

                        {milestones.length > MIN_PLAN_MILESTONES && (
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
                          <label
                            htmlFor={`milestone-${idx}-target-date`}
                            className="block text-[11px] font-bold text-neutral-700 mb-1"
                          >
                            Hạn dự kiến
                            {idx === milestones.length - 1 ? <span className="text-rose-500"> *</span> : " (tùy chọn)"}
                          </label>
                          <div className="relative">
                            <input
                              id={`milestone-${idx}-target-date`}
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

                      {!isEditing && saveAsTemplate && (
                        <label className="flex items-center gap-2 text-[11px] font-bold text-neutral-700">
                          Số buổi gợi ý cho mẫu
                          <select
                            aria-label={`Số buổi gợi ý của cột mốc ${idx + 1}`}
                            value={item.suggestedSessions}
                            onChange={(e) => handleMilestoneChange(idx, "suggestedSessions", Number(e.target.value))}
                            className="rounded-lg border border-stone-200 bg-white px-2 py-1 text-xs"
                          >
                            {Array.from(
                              { length: MAX_SUGGESTED_SESSIONS - MIN_SUGGESTED_SESSIONS + 1 },
                              (_, i) => MIN_SUGGESTED_SESSIONS + i
                            ).map((n) => (
                              <option key={n} value={n}>
                                {n} buổi
                              </option>
                            ))}
                          </select>
                        </label>
                      )}

                      {/* Tiêu chí đạt */}
                      <div className="space-y-2 rounded-xl border border-stone-200/70 bg-stone-50/40 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="flex items-center gap-1.5 text-[11px] font-bold text-neutral-700">
                            <ListChecks className="size-3.5 text-amber-600" />
                            Tiêu chí đạt (1–{MAX_CRITERIA_PER_MILESTONE}) <span className="text-rose-500">*</span>
                          </span>
                          <button
                            type="button"
                            onClick={() => handleAddCriterion(idx)}
                            disabled={item.criteria.length >= MAX_CRITERIA_PER_MILESTONE}
                            className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-bold text-amber-700 hover:bg-amber-50 disabled:opacity-40 cursor-pointer"
                          >
                            <Plus className="size-3" />
                            Thêm tiêu chí
                          </button>
                        </div>
                        {item.criteria.map((criterion, cIdx) => (
                          <div key={criterion.criterionId ?? `new-${cIdx}`} className="flex items-center gap-2">
                            <input
                              type="text"
                              value={criterion.content}
                              onChange={(e) => handleCriterionChange(idx, cIdx, e.target.value)}
                              placeholder="Ví dụ: Tự giải đúng hệ phương trình bằng phương pháp thế"
                              aria-label={`Tiêu chí ${cIdx + 1} của cột mốc ${idx + 1}`}
                              className="flex-1 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-xs text-neutral-900 placeholder:text-neutral-400 focus:border-amber-500 focus:outline-hidden"
                              maxLength={200}
                            />
                            {item.criteria.length > 1 && (
                              <button
                                type="button"
                                onClick={() => handleRemoveCriterion(idx, cIdx)}
                                className="rounded-lg p-1 text-neutral-400 hover:bg-rose-50 hover:text-rose-600 cursor-pointer"
                                aria-label={`Xóa tiêu chí ${cIdx + 1} của cột mốc ${idx + 1}`}
                              >
                                <Trash2 className="size-3.5" />
                              </button>
                            )}
                          </div>
                        ))}
                        {criteriaError && <p className="text-[10px] text-rose-600">{criteriaError}</p>}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {!isEditing && (
              <div className="space-y-2 rounded-2xl border border-stone-200 p-3">
                <label className="flex items-center gap-2 text-xs font-bold text-neutral-800">
                  <input
                    type="checkbox"
                    className="size-4"
                    checked={saveAsTemplate}
                    onChange={(e) => setSaveAsTemplate(e.target.checked)}
                  />
                  Lưu lộ trình này thành mẫu của tôi
                </label>
                {saveAsTemplate && (
                  <input
                    type="text"
                    aria-label="Tên mẫu lộ trình"
                    value={templateTitle}
                    maxLength={MAX_TEMPLATE_TITLE_LENGTH}
                    onChange={(e) => setTemplateTitle(e.target.value)}
                    placeholder="Ví dụ: Toán 9 ôn thi vào 10 (mẫu của tôi)"
                    className="w-full rounded-xl border border-stone-200 bg-stone-50/40 px-3 py-2 text-xs"
                  />
                )}
              </div>
            )}
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
