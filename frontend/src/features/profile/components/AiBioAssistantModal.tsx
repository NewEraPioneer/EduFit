"use client";

import React, { useState } from "react";
import {
  Sparkles,
  X,
  Check,
  RotateCcw,
  Copy,
  BookOpen,
  GraduationCap,
  Clock,
  Loader2,
  Smile,
  Award,
  Zap,
} from "lucide-react";
import { useToast } from "@/shared/ui/Toast";
import { profileService } from "@/services/profile";

export interface AiBioAssistantModalProps {
  isOpen: boolean;
  onClose: () => void;
  displayName?: string;
  subjects?: string[];
  educationLevels?: string[];
  experienceYears?: number;
  currentBio?: string;
  onApplyBio: (bio: string) => void;
}

export type AiTone = "friendly" | "professional" | "concise";

const MAX_BIO_LENGTH = 2000;

export function AiBioAssistantModal({
  isOpen,
  onClose,
  displayName = "Gia sư",
  subjects = [],
  educationLevels = [],
  experienceYears = 1,
  currentBio = "",
  onApplyBio,
}: AiBioAssistantModalProps) {
  const { showToast } = useToast();

  const [tone, setTone] = useState<AiTone>("friendly");
  const [focusArea, setFocusArea] = useState<string>("exam"); // "exam" | "foundation" | "mindset"
  const [userDraftBio, setUserDraftBio] = useState<string>(currentBio || "");
  const [isGenerating, setIsGenerating] = useState(false);
  const [draftBio, setDraftBio] = useState<string | null>(null);
  const [isAiGenerated, setIsAiGenerated] = useState<boolean>(true);
  const [remainingQuota, setRemainingQuota] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);
  const [agreed, setAgreed] = useState(true);

  React.useEffect(() => {
    if (isOpen) {
      setUserDraftBio(currentBio || "");
    }
  }, [isOpen, currentBio]);

  if (!isOpen) return null;

  const subjectNamesText = subjects.length > 0 ? subjects.join(", ") : "các môn học";
  const levelsText = educationLevels.length > 0 ? educationLevels.join(", ") : "các khối lớp";

  const handleGenerate = async () => {
    if (!agreed) {
      showToast("Đồng ý điều khoản để tạo bản nháp giới thiệu.", "error");
      return;
    }

    const sourceBio = draftBio?.trim() || userDraftBio.trim();
    setIsGenerating(true);
    try {
      const res = await profileService.generateTutorBio({
        displayName,
        subjects,
        educationLevels,
        experienceYears,
        tone,
        focusArea,
        currentBio: sourceBio.slice(0, MAX_BIO_LENGTH) || undefined,
      });

      setDraftBio(res.bio);
      setIsAiGenerated(res.isAiGenerated);
      setRemainingQuota(res.remainingQuota);
    } catch (err: unknown) {
      console.error("Lỗi khi sinh bio AI:", err);
      // Fallback nếu gặp lỗi mạng
      const name = displayName.trim() || "Gia sư";
      const exp = experienceYears > 0 ? `${experienceYears} năm` : "nhiều năm";
      const fallbackBio = sourceBio
        ? `${sourceBio} Với ${exp} kinh nghiệm chuyên môn môn ${subjectNamesText} (${levelsText}), tôi luôn nỗ lực đồng hành giúp học sinh thấu hiểu bản chất bài học và tự tin nâng điểm số.`
        : `Chào các em và quý phụ huynh! Tôi là ${name}, chuyên giảng dạy ${subjectNamesText} (${levelsText}). Với ${exp} kinh nghiệm, phong cách giảng dạy của tôi luôn lấy sự kiên nhẫn và thấu hiểu làm trọng tâm, giúp học sinh nắm chắc kiến thức và tự tin đạt điểm số cao!`;
      setDraftBio(fallbackBio);
      setIsAiGenerated(false);
      const msg = err instanceof Error ? err.message : "Dịch vụ AI tạm thời gián đoạn. Đã tải bản thảo dự phòng.";
      showToast(msg, "info");
    } finally {
      setIsGenerating(false);
    }
  };

  const handleApply = () => {
    if (draftBio) {
      onApplyBio(draftBio);
      showToast("Đã áp dụng bản thảo Bio vào hồ sơ thành công!", "success");
      onClose();
    }
  };

  const handleCopy = async () => {
    if (draftBio) {
      await navigator.clipboard.writeText(draftBio);
      setCopied(true);
      showToast("Đã sao chép vào bộ nhớ tạm!", "info");
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/50 backdrop-blur-xs animate-in fade-in duration-150">
      <div
        className="relative w-full max-w-dialog bg-surface rounded-3xl shadow-2xl border border-line overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 1. Header Modal */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-line bg-action/10">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-action text-on-action rounded-xl shadow-xs">
              <Sparkles className="size-4" />
            </div>
            <div>
              <h3 className="text-sm font-medium text-ink flex items-center gap-1.5">
                <span>Trợ lý AI viết Bio gia sư</span>
                <span className="text-xs font-medium px-2 py-0.5 bg-action-soft text-action-strong rounded-full">
                  EduFit AI
                </span>
              </h3>
              <p className="text-xs text-muted">
                Tạo bản giới thiệu thu hút phụ huynh dựa trên thông tin chuyên môn của bạn
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-muted hover:text-ink-2 hover:bg-sunken rounded-xl transition-colors cursor-pointer"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* 2. Modal Body */}
        <div className="p-6 overflow-y-auto space-y-5 text-xs">
          {/* Thông tin chuyên môn đầu vào */}
          <div className="p-3 bg-paper rounded-2xl border border-line/70 space-y-1.5">
            <span className="text-xs font-medium text-muted block">
              Dữ liệu chuyên môn được áp dụng
            </span>
            <div className="flex flex-wrap items-center gap-3 text-ink-2">
              <div className="flex items-center gap-1">
                <BookOpen className="size-3 text-action-strong" />
                <span>Môn: <strong>{subjectNamesText}</strong></span>
              </div>
              <div className="flex items-center gap-1">
                <GraduationCap className="size-3 text-brand-strong" />
                <span>Khối: <strong>{levelsText}</strong></span>
              </div>
              <div className="flex items-center gap-1">
                <Clock className="size-3 text-success-strong" />
                <span>Kinh nghiệm: <strong>{experienceYears} năm</strong></span>
              </div>
            </div>
          </div>

          {/* Chọn Giọng văn (Tone) */}
          <div className="space-y-2">
            <label className="font-medium text-ink block">
              1. Chọn phong cách giọng văn (Tone)
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[
                {
                  id: "friendly" as AiTone,
                  label: "Thân thiện",
                  desc: "Gần gũi, kiên nhẫn",
                  icon: Smile,
                },
                {
                  id: "professional" as AiTone,
                  label: "Chuyên nghiệp",
                  desc: "Chuẩn mực sư phạm",
                  icon: Award,
                },
                {
                  id: "concise" as AiTone,
                  label: "Ngắn gọn",
                  desc: "Trọng tâm, súc tích",
                  icon: Zap,
                },
              ].map((item) => {
                const Icon = item.icon;
                const isSelected = tone === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setTone(item.id)}
                    className={`p-3 rounded-2xl border text-left transition-all cursor-pointer ${
                      isSelected
                        ? "border-action bg-action-soft/70 text-action-strong shadow-xs"
                        : "border-line hover:border-line-strong bg-surface text-ink-2"
                    }`}
                  >
                    <Icon className={`size-4 mb-1 ${isSelected ? "text-action-strong" : "text-muted"}`} />
                    <span className="font-medium block text-xs">{item.label}</span>
                    <span className="text-xs text-muted block mt-0.5">{item.desc}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Chọn Điểm nhấn trọng tâm */}
          <div className="space-y-2">
            <label className="font-medium text-ink block">
              2. Điểm nhấn giảng dạy mong muốn
            </label>
            <div className="flex flex-wrap gap-2">
              {[
                { id: "exam", label: "Luyện thi & Bứt phá điểm số" },
                { id: "foundation", label: "Lấy lại căn bản & Xóa mất gốc" },
                { id: "mindset", label: "Rèn luyện tư duy tự học & Logic" },
              ].map((f) => (
                <button
                  key={f.id}
                  type="button"
                  onClick={() => setFocusArea(f.id)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                    focusArea === f.id
                      ? "bg-night text-white shadow-xs"
                      : "bg-sunken hover:bg-line text-ink-2"
                  }`}
                >
                  {focusArea === f.id && <Check className="size-3 inline-block mr-1" />}
                  {f.label}
                </button>
              ))}
            </div>
          </div>

          {/* 3. Bản nháp / Ý tưởng cá nhân của gia sư */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="font-medium text-ink block">
                3. Ý tưởng / Bản nháp của bạn (Tùy chọn)
              </label>
              <span className="text-xs text-muted">
                {userDraftBio.length}/{MAX_BIO_LENGTH} ký tự
              </span>
            </div>
            <textarea
              rows={3}
              maxLength={MAX_BIO_LENGTH}
              value={userDraftBio}
              onChange={(e) => setUserDraftBio(e.target.value)}
              placeholder="Nhập đoạn tiểu sử bạn đã tự viết hoặc các ý tưởng/thế mạnh riêng (vd: phương pháp sơ đồ tư duy, kiên nhẫn với học sinh mất gốc)..."
              className="w-full px-3 py-2 text-xs border border-line rounded-xl focus:outline-none focus:ring-2 focus:ring-action/20 focus:border-action text-ink placeholder:text-muted resize-none transition-all"
            />
            <p className="text-xs text-muted leading-normal">
              AI sẽ chắt lọc ý hay từ bản nháp của bạn và chuẩn hóa theo thông tin hồ sơ (môn học, số năm kinh nghiệm).
            </p>
          </div>

          {/* Điều khoản */}
          <label className="flex items-center gap-2 text-xs text-ink-2 cursor-pointer pt-1">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(e) => setAgreed(e.target.checked)}
              className="rounded text-action-strong focus:ring-action"
            />
            <span>Đồng ý để EduFit xử lý thông tin để tạo bản thảo bio tùy biến.</span>
          </label>

          {/* Nút Tạo Bio */}
          <div>
            <button
              type="button"
              disabled={isGenerating}
              onClick={handleGenerate}
              className="w-full inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
            >
              {isGenerating ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  <span>AI đang soạn thảo bio...</span>
                </>
              ) : (
                <>
                  <Sparkles className="size-3.5" />
                  <span>{draftBio !== null ? "Soạn thảo lại với AI" : "Tạo bản thảo Bio bằng AI"}</span>
                </>
              )}
            </button>
          </div>

          {/* Hiển thị Bản thảo AI gợi ý */}
          {draftBio !== null && (
            <div className="p-4 bg-action-soft/50 border border-action-line/90 rounded-2xl space-y-3 animate-in fade-in">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium text-action-strong flex items-center gap-1.5">
                  <Sparkles className="size-3 text-action-strong" />
                  {isAiGenerated ? "BẢN THẢO TỪ GEMINI AI" : "BẢN THẢO GỢI Ý (DỰ PHÒNG)"}
                </span>
                <div className="flex items-center gap-2">
                  {remainingQuota !== null && (
                    <span className="text-xs bg-action-soft text-action-strong font-medium px-2 py-0.5 rounded-full">
                      Còn {remainingQuota} lượt/giờ
                    </span>
                  )}
                  <span className="text-muted font-medium">{draftBio.length} ký tự</span>
                </div>
              </div>

              <label className="block space-y-1 text-xs font-medium text-ink-2">
                Chỉnh sửa bản thảo trước khi dùng
                <textarea
                  value={draftBio}
                  readOnly={isGenerating}
                  onChange={(event) => setDraftBio(event.target.value)}
                  maxLength={MAX_BIO_LENGTH}
                  rows={6}
                  className="w-full resize-y rounded-xl border border-line bg-surface p-3.5 text-xs font-normal leading-relaxed text-ink focus:border-action focus:outline-none focus:ring-2 focus:ring-action/20"
                />
              </label>

              <div className="flex items-center justify-between gap-2 pt-1">
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleApply}
                    disabled={isGenerating || !draftBio.trim()}
                    className="inline-flex items-center gap-1.5 px-4 py-2 bg-success hover:bg-success-deep text-white font-medium rounded-xl shadow-xs transition-colors cursor-pointer disabled:opacity-50"
                  >
                    <Check className="size-3.5" />
                    <span>Dùng bản thảo này</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleCopy}
                    disabled={!draftBio.trim()}
                    className="inline-flex items-center gap-1.5 px-3 py-2 bg-surface hover:bg-paper border border-line text-ink-2 rounded-xl transition-colors cursor-pointer"
                    title="Sao chép văn bản"
                  >
                    {copied ? <Check className="size-3.5 text-success-strong" /> : <Copy className="size-3.5" />}
                    <span>{copied ? "Đã chép" : "Sao chép"}</span>
                  </button>
                </div>

                <button
                  type="button"
                  onClick={handleGenerate}
                  disabled={isGenerating}
                  className="inline-flex items-center gap-1 text-xs text-muted hover:text-ink cursor-pointer"
                >
                  <RotateCcw className="size-3" />
                  <span>Đổi bản khác</span>
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 3. Footer Modal */}
        <div className="px-6 py-3 border-t border-line bg-paper/60 flex items-center justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 text-xs font-medium text-muted hover:text-ink transition-colors cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
