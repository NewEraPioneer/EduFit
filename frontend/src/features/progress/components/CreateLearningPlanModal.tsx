"use client";

import { DateField } from "@/shared/ui/DateField";
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
  Sparkles,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import { createPlanSchema, CreatePlanInput, planTemplateSchema } from "../schemas/progressSchemas";
import type { PlanDraft, PlanResponse, PlanTemplate, TemplateMilestone, Topic } from "../types";
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
  /** Số buổi gợi ý (tùy chọn) cho dự báo "còn X buổi"; null = chưa đặt. Lưu thành mẫu thì dùng mặc định. */
  suggestedSessions: number | null;
  /** Chủ đề chuẩn (tùy chọn); "" = không gắn. */
  topicId: string;
  criteria: CriterionFormField[];
}

const emptyMilestone = (): MilestoneFormField => ({
  title: "",
  description: "",
  targetDate: "",
  weight: 1,
  suggestedSessions: null,
  topicId: "",
  criteria: [{ content: "" }],
});

const SESSION_OPTIONS = Array.from(
  { length: MAX_SUGGESTED_SESSIONS - MIN_SUGGESTED_SESSIONS + 1 },
  (_, i) => MIN_SUGGESTED_SESSIONS + i
);

const emptyRoadmap = (): MilestoneFormField[] => Array.from({ length: MIN_PLAN_MILESTONES }, emptyMilestone);

/** Nhân bản mẫu vào biểu mẫu: giữ nội dung, bỏ trống hạn để gia sư đặt theo lịch của lớp. */
const fromTemplate = (m: TemplateMilestone): MilestoneFormField => ({
  title: m.title,
  description: m.description ?? "",
  targetDate: "",
  weight: m.weight,
  suggestedSessions: m.suggestedSessions,
  topicId: "",
  criteria: m.criteria.map((content) => ({ content })),
});

/** Bản nháp AI vào biểu mẫu: như mẫu, kèm chủ đề nếu AI chọn đúng tên trong danh mục. */
const fromDraft = (m: PlanDraft["milestones"][number]): MilestoneFormField => ({
  ...fromTemplate(m),
  topicId: m.topicId ?? "",
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
  const [topics, setTopics] = useState<Topic[]>([]);
  const [drafting, setDrafting] = useState(false);
  const [draftNotice, setDraftNotice] = useState<string | null>(null);
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
              suggestedSessions: m.suggestedSessions ?? null,
              topicId: m.topicId ?? "",
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
      setDraftNotice(null);
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

  // Chủ đề chuẩn của môn × khối của lớp (cả khi sửa); lỗi hoặc chưa có chủ đề thì ẩn ô chọn chủ đề
  useEffect(() => {
    if (!isOpen) return;
    let ignore = false;
    progressApi
      .listTopics(classId)
      .then((res) => {
        if (!ignore) setTopics(res.success && res.data ? res.data : []);
      })
      .catch(() => {
        if (!ignore) setTopics([]);
      });
    return () => {
      ignore = true;
    };
  }, [isOpen, classId]);

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

  const handleDraftWithAi = async () => {
    setErrorMessage(null);
    setFieldErrors({});
    setDraftNotice(null);
    setDrafting(true);
    try {
      const res = await progressApi.draftPlan(classId);
      const draft = res.data;
      if (!res.success || !draft) {
        setErrorMessage(res.message || "Không gợi ý được lộ trình. Hãy chọn mẫu lộ trình hoặc tự lập.");
      } else if (draft.milestones.length === 0) {
        setErrorMessage(draft.unavailableReason || "Không gợi ý được lộ trình. Hãy chọn mẫu lộ trình hoặc tự lập.");
      } else {
        setMilestones(draft.milestones.map(fromDraft));
        setDraftNotice(
          `Đã điền bản nháp AI (còn ${draft.remainingQuota} lượt trong giờ). Hãy đọc lại, sửa và đặt hạn cho cột mốc cuối trước khi lưu.`
        );
      }
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : "Không gợi ý được lộ trình.");
    } finally {
      setDrafting(false);
    }
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
      suggestedSessions: m.suggestedSessions ?? DEFAULT_SUGGESTED_SESSIONS,
      criteria: filledCriteria(m.criteria).map((c) => c.content),
    })),
  });

  const handleMilestoneChange = (
    index: number,
    field: keyof MilestoneFormField,
    value: string | number | null
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
        suggestedSessions: m.suggestedSessions ?? undefined,
        topicId: m.topicId || undefined,
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
            suggestedSessions: m.suggestedSessions ?? undefined,
            topicId: m.topicId || undefined,
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
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs overflow-y-auto"
    >
      <div className="relative w-full max-w-dialog-wide overflow-hidden rounded-3xl bg-surface shadow-2xl border border-line animate-in fade-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-line px-6 py-4 bg-paper/50">
          <div className="flex items-center gap-3">
            <div className="grid size-10 place-items-center rounded-2xl bg-action-soft text-action-strong">
              <Award className="size-5" />
            </div>
            <div>
              <h2 id="plan-modal-title" className="text-base font-medium text-ink">
                {isEditing ? "Cập nhật Kế hoạch học tập" : "Thiết lập Kế hoạch học tập & Cột mốc"}
              </h2>
              <p className="text-xs text-muted mt-0.5">
                {className ? `${className}` : "Lớp học kèm 1:1"}{" "}
                {studentName ? `• Học sinh: ${studentName}` : ""}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-muted hover:bg-sunken hover:text-ink-2 transition-colors"
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
                className="flex items-start gap-3 rounded-2xl bg-danger-soft border border-danger-line p-4 text-xs text-danger-strong font-medium"
              >
                <AlertCircle className="size-4.5 text-danger-strong shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium">Không thể lưu kế hoạch</p>
                  <p className="mt-0.5">{errorMessage}</p>
                </div>
              </div>
            )}

            {!isEditing && (
              <div className="space-y-2 rounded-2xl border border-brand-line bg-brand-soft/50 p-3" data-testid="plan-ai-draft">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs text-ink-2">
                    <span className="font-medium text-ink">Gợi ý bằng AI:</span> lập bản nháp theo môn, khối, mục
                    tiêu của lớp. Không gửi thông tin cá nhân của học sinh.
                  </p>
                  <button
                    type="button"
                    onClick={() => void handleDraftWithAi()}
                    disabled={drafting}
                    className="inline-flex items-center gap-1.5 min-h-9 px-3 rounded-xl bg-brand text-white text-xs font-medium disabled:opacity-50 cursor-pointer"
                  >
                    {drafting ? <Loader2 className="size-3.5 animate-spin" /> : <Sparkles className="size-3.5" />}
                    {drafting ? "Đang gợi ý…" : "Gợi ý lộ trình bằng AI"}
                  </button>
                </div>
                {draftNotice && (
                  <p role="status" className="text-xs text-brand-strong">
                    {draftNotice}
                  </p>
                )}
              </div>
            )}

            {!isEditing && templates.length > 0 && (
              <div className="space-y-2 rounded-2xl border border-action-line bg-action-soft/50 p-3" data-testid="plan-template-picker">
                <label htmlFor="plan-template" className="block text-xs font-medium text-ink">
                  Bắt đầu từ mẫu lộ trình
                </label>
                <div className="flex flex-wrap items-center gap-2">
                  <select
                    id="plan-template"
                    value={selectedTemplateId}
                    onChange={(e) => setSelectedTemplateId(e.target.value)}
                    className="min-h-9 flex-1 rounded-xl border border-line bg-surface px-2 text-xs"
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
                    className="min-h-9 px-3 rounded-xl bg-action text-on-action text-xs font-medium disabled:opacity-40 cursor-pointer"
                  >
                    Áp dụng mẫu
                  </button>
                  {selectedTemplate?.mine && (
                    <button
                      type="button"
                      onClick={() => void handleDeleteTemplate()}
                      className="min-h-9 px-3 rounded-xl border border-danger-line bg-surface text-danger-strong text-xs font-medium cursor-pointer"
                    >
                      Xóa mẫu này
                    </button>
                  )}
                </div>
                <p className="text-xs text-muted">
                  Áp dụng sẽ thay danh sách cột mốc bên dưới; bạn vẫn sửa được trước khi lưu và cần đặt hạn cho cột mốc cuối.
                </p>
              </div>
            )}

            {/* Summary */}
            <div className="space-y-1.5">
              <label
                htmlFor="plan-summary"
                className="block text-xs font-medium text-ink"
              >
                Mục tiêu chung & Tóm tắt kế hoạch
              </label>
              <textarea
                id="plan-summary"
                rows={3}
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="Ví dụ: Ôn tập lấy lại căn bản môn Toán lớp 9, làm quen các dạng bài tập hình học và phương trình bậc hai hướng tới kỳ thi vào 10..."
                className="w-full rounded-2xl border border-line bg-paper/50 p-3 text-xs text-ink placeholder:text-muted focus:bg-surface focus:border-action focus:ring-2 focus:ring-action/20 focus:outline-hidden transition-all"
                maxLength={1000}
              />
              <div className="flex justify-between text-xs text-muted px-1">
                <span>Tóm tắt tổng quát giúp học sinh và phụ huynh nắm rõ lộ trình</span>
                <span>{summary.length}/1000</span>
              </div>
            </div>

            {/* Milestones Header & Quick Stats */}
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-2">
                <div className="flex items-center gap-2">
                  <h3 className="text-xs font-medium text-ink">
                    Danh sách các cột mốc kiến thức ({MIN_PLAN_MILESTONES} - {MAX_PLAN_MILESTONES} mốc)
                  </h3>
                  <span className="inline-flex items-center rounded-full bg-action-soft px-2 py-0.5 text-xs font-medium text-action-strong border border-action-line">
                    {milestones.length} mốc • Tổng trọng số: {totalWeight}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={handleAddMilestone}
                  disabled={milestones.length >= MAX_PLAN_MILESTONES}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-action px-3 py-1.5 text-xs font-medium text-on-action shadow-xs hover:bg-action-deep active:scale-95 disabled:opacity-50 transition-all cursor-pointer"
                >
                  <Plus className="size-3.5" />
                  Thêm cột mốc
                </button>
              </div>

              {/* Formula Explainer */}
              <div className="flex items-center gap-2 rounded-xl bg-paper p-3 text-xs text-ink-2 border border-line/60">
                <HelpCircle className="size-4 text-action-strong shrink-0" />
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
                      className="relative rounded-2xl border border-line bg-surface p-4 shadow-xs space-y-3 transition-shadow hover:shadow-md"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="grid size-6 place-items-center rounded-lg bg-sunken text-xs font-medium text-ink-2">
                            {idx + 1}
                          </span>
                          <span className="text-xs font-medium text-ink">
                            Cột mốc {idx + 1}
                          </span>
                        </div>

                        {milestones.length > MIN_PLAN_MILESTONES && (
                          <button
                            type="button"
                            onClick={() => handleRemoveMilestone(idx)}
                            className="rounded-lg p-1 text-muted hover:bg-danger-soft hover:text-danger-strong transition-colors cursor-pointer"
                            title="Xóa cột mốc này"
                            aria-label={`Xóa cột mốc ${idx + 1}`}
                          >
                            <Trash2 className="size-4" />
                          </button>
                        )}
                      </div>

                      {/* Title input */}
                      <div>
                        <label className="block text-xs font-medium text-ink-2 mb-1">
                          Tiêu đề cột mốc <span className="text-danger-strong">*</span>
                        </label>
                        <input
                          type="text"
                          value={item.title}
                          onChange={(e) => handleMilestoneChange(idx, "title", e.target.value)}
                          placeholder="Ví dụ: Nắm vững Định lý Vi-ét và ứng dụng giải bài toán"
                          className={`w-full rounded-xl border px-3 py-2 text-xs text-ink placeholder:text-muted focus:outline-hidden ${
                            titleError
                              ? "border-danger-line bg-danger-soft/30 focus:border-danger"
                              : "border-line bg-paper/40 focus:bg-surface focus:border-action focus:ring-1 focus:ring-action"
                          }`}
                          maxLength={200}
                        />
                        {titleError && (
                          <p className="text-xs text-danger-strong mt-1">{titleError}</p>
                        )}
                      </div>

                      {/* Description & Date & Weight Row */}
                      <div className="grid grid-cols-1 sm:grid-cols-12 gap-3">
                        <div className="sm:col-span-6">
                          <label className="block text-xs font-medium text-ink-2 mb-1">
                            Mô tả chi tiết (Tùy chọn)
                          </label>
                          <input
                            type="text"
                            value={item.description}
                            onChange={(e) =>
                              handleMilestoneChange(idx, "description", e.target.value)
                            }
                            placeholder="Chi tiết bài tập, kỹ năng cần thành thạo..."
                            className="w-full rounded-xl border border-line bg-paper/40 px-3 py-2 text-xs text-ink placeholder:text-muted focus:bg-surface focus:border-action focus:outline-hidden"
                            maxLength={1000}
                          />
                        </div>

                        <div className="sm:col-span-3">
                          <label
                            htmlFor={`milestone-${idx}-target-date`}
                            className="block text-xs font-medium text-ink-2 mb-1"
                          >
                            Hạn dự kiến
                            {idx === milestones.length - 1 ? <span className="text-danger-strong"> *</span> : " (tùy chọn)"}
                          </label>
                          <div className="relative">
                            <DateField
                              id={`milestone-${idx}-target-date`}
                              value={item.targetDate}
                              onChange={(value) => handleMilestoneChange(idx, "targetDate", value)}
                            />
                          </div>
                        </div>

                        <div className="sm:col-span-3">
                          <label className="block text-xs font-medium text-ink-2 mb-1">
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
                                  className={`flex-1 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                                    isSelected
                                      ? "bg-action text-on-action shadow-xs"
                                      : "bg-sunken text-ink-2 hover:bg-line"
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

                      <div className="flex flex-wrap gap-3 text-xs font-medium text-ink-2">
                        <label className="flex items-center gap-2">
                          Số buổi gợi ý
                          <select
                            aria-label={`Số buổi gợi ý của cột mốc ${idx + 1}`}
                            value={item.suggestedSessions ?? ""}
                            onChange={(e) =>
                              handleMilestoneChange(idx, "suggestedSessions", e.target.value ? Number(e.target.value) : null)
                            }
                            className="rounded-lg border border-line bg-surface px-2 py-1 text-xs"
                          >
                            <option value="">Chưa đặt</option>
                            {SESSION_OPTIONS.map((n) => (
                              <option key={n} value={n}>
                                {n} buổi
                              </option>
                            ))}
                          </select>
                        </label>
                        {(topics.length > 0 || item.topicId) && (
                          <label className="flex items-center gap-2">
                            Chủ đề chuẩn
                            <select
                              aria-label={`Chủ đề chuẩn của cột mốc ${idx + 1}`}
                              value={item.topicId}
                              onChange={(e) => handleMilestoneChange(idx, "topicId", e.target.value)}
                              className="rounded-lg border border-line bg-surface px-2 py-1 text-xs"
                            >
                              <option value="">Không gắn</option>
                              {topics.map((t) => (
                                <option key={t.topicId} value={t.topicId}>
                                  {t.name}
                                </option>
                              ))}
                              {item.topicId && !topics.some((t) => t.topicId === item.topicId) && (
                                <option value={item.topicId}>Chủ đề đã ngừng dùng</option>
                              )}
                            </select>
                          </label>
                        )}
                      </div>

                      {/* Tiêu chí đạt */}
                      <div className="space-y-2 rounded-xl border border-line/70 bg-paper/40 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="flex items-center gap-1.5 text-xs font-medium text-ink-2">
                            <ListChecks className="size-3.5 text-action-strong" />
                            Tiêu chí đạt (1–{MAX_CRITERIA_PER_MILESTONE}) <span className="text-danger-strong">*</span>
                          </span>
                          <button
                            type="button"
                            onClick={() => handleAddCriterion(idx)}
                            disabled={item.criteria.length >= MAX_CRITERIA_PER_MILESTONE}
                            className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-action-strong hover:bg-action-soft disabled:opacity-40 cursor-pointer"
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
                              className="flex-1 rounded-lg border border-line bg-surface px-3 py-1.5 text-xs text-ink placeholder:text-muted focus:border-action focus:outline-hidden"
                              maxLength={200}
                            />
                            {item.criteria.length > 1 && (
                              <button
                                type="button"
                                onClick={() => handleRemoveCriterion(idx, cIdx)}
                                className="rounded-lg p-1 text-muted hover:bg-danger-soft hover:text-danger-strong cursor-pointer"
                                aria-label={`Xóa tiêu chí ${cIdx + 1} của cột mốc ${idx + 1}`}
                              >
                                <Trash2 className="size-3.5" />
                              </button>
                            )}
                          </div>
                        ))}
                        {criteriaError && <p className="text-xs text-danger-strong">{criteriaError}</p>}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {!isEditing && (
              <div className="space-y-2 rounded-2xl border border-line p-3">
                <label className="flex items-center gap-2 text-xs font-medium text-ink">
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
                    className="w-full rounded-xl border border-line bg-paper/40 px-3 py-2 text-xs"
                  />
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t border-line px-6 py-4 bg-paper/50">
            <span className="text-xs text-muted">
              {milestones.length} cột mốc sẽ được tạo
            </span>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="h-9 px-4 rounded-xl border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper active:scale-95 transition-all cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="submit"
                disabled={loading}
                className="h-9 px-5 rounded-xl bg-action hover:bg-action-deep active:scale-95 text-on-action text-xs font-medium shadow-xs flex items-center gap-1.5 cursor-pointer disabled:opacity-50 transition-all"
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
