"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { profileService } from "@/services/profile";
import { catalogService, EducationLevel, SubjectItem } from "@/services/catalog";
import {
  GraduationCap,
  BookOpen,
  ArrowRight,
  ArrowLeft,
  CheckCircle2,
  Lightbulb,
  MapPin,
  Laptop,
  Users,
  Compass,
  Target,
  Loader2,
} from "lucide-react";

export function StudentOnboardingWizard() {
  const router = useRouter();
  const { user, refreshUser } = useAuth();
  const { showToast } = useToast();

  const [step, setStep] = useState<1 | 2>(1);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Catalogs
  const [educationLevels, setEducationLevels] = useState<EducationLevel[]>([]);
  const [subjectsCatalog, setSubjectsCatalog] = useState<SubjectItem[]>([]);

  // Step 1: Chọn lớp
  const [selectedLevelId, setSelectedLevelId] = useState<number | null>(null);

  // Step 2: Môn học, Khu vực, Hình thức & Mục tiêu
  const [selectedSubjectIds, setSelectedSubjectIds] = useState<number[]>([]);
  const [area, setArea] = useState<string>("");
  const [learningMode, setLearningMode] = useState<"ONLINE" | "OFFLINE" | "BOTH">("BOTH");
  const [goalTitle, setGoalTitle] = useState<string>("");

  useEffect(() => {
    async function loadCatalogs() {
      try {
        setLoading(true);
        const [levels, subjects, profile] = await Promise.all([
          catalogService.getEducationLevels().catch(() => [
            { levelId: 1, name: "Lớp 6", sortOrder: 1 },
            { levelId: 2, name: "Lớp 7", sortOrder: 2 },
            { levelId: 3, name: "Lớp 8", sortOrder: 3 },
            { levelId: 4, name: "Lớp 9", sortOrder: 4 },
          ]),
          catalogService.getSubjects().catch(() => [
            { subjectId: 1, name: "Toán" },
            { subjectId: 2, name: "Ngữ văn" },
            { subjectId: 3, name: "Tiếng Anh" },
            { subjectId: 4, name: "Vật lý" },
            { subjectId: 5, name: "Hóa học" },
            { subjectId: 6, name: "Sinh học" },
          ]),
          profileService.getMyStudentProfile().catch(() => null),
        ]);

        setEducationLevels(levels);
        setSubjectsCatalog(subjects);

        if (profile) {
          if (profile.educationLevelId) setSelectedLevelId(profile.educationLevelId);
          if (profile.area) setArea(profile.area);
        }
      } catch (err) {
        console.error("Lỗi khi tải danh mục onboarding:", err);
      } finally {
        setLoading(false);
      }
    }

    loadCatalogs();
  }, []);

  const toggleSubject = (id: number) => {
    setSelectedSubjectIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleNextStep = () => {
    if (!selectedLevelId) {
      showToast("Vui lòng chọn khối lớp đang theo học!", "error");
      return;
    }
    setStep(2);
  };

  const handleComplete = async () => {
    if (!selectedLevelId) {
      showToast("Vui lòng chọn khối lớp học!", "error");
      setStep(1);
      return;
    }

    if (!area.trim()) {
      showToast("Vui lòng nhập khu vực cư trú để tìm gia sư gần bạn!", "error");
      return;
    }

    if (selectedSubjectIds.length === 0) {
      showToast("Vui lòng chọn ít nhất 1 môn học quan tâm!", "error");
      return;
    }

    setSubmitting(true);
    try {
      // 1. Cập nhật hồ sơ học viên lên Backend
      await profileService.updateStudentProfile({
        educationLevelId: selectedLevelId,
        area: area.trim(),
      });

      // 2. Khởi tạo mục tiêu học tập ban đầu trên Backend cho TOÀN BỘ các môn học sinh đã lựa chọn
      let resolvedGoalType: "CONSOLIDATE" | "IMPROVE_GRADES" | "ADVANCED_STUDY" | "EXAM_PREPARATION" = "EXAM_PREPARATION";
      const normalizedTitle = goalTitle.trim().toLowerCase();
      if (normalizedTitle.includes("củng cố") || normalizedTitle.includes("căn bản") || normalizedTitle.includes("mất gốc")) {
        resolvedGoalType = "CONSOLIDATE";
      } else if (normalizedTitle.includes("cải thiện") || normalizedTitle.includes("nâng điểm") || normalizedTitle.includes("điểm số")) {
        resolvedGoalType = "IMPROVE_GRADES";
      } else if (normalizedTitle.includes("bồi dưỡng") || normalizedTitle.includes("chuyên") || normalizedTitle.includes("hsg") || normalizedTitle.includes("nâng cao")) {
        resolvedGoalType = "ADVANCED_STUDY";
      } else if (normalizedTitle.includes("thi") || normalizedTitle.includes("vào 10") || normalizedTitle.includes("đại học") || normalizedTitle.includes("chuyển cấp")) {
        resolvedGoalType = "EXAM_PREPARATION";
      }

      const goalDeadline = new Date(Date.now() + 180 * 24 * 60 * 60 * 1000).toISOString().split("T")[0];
      const goalArea = learningMode === "ONLINE" ? undefined : (area.trim() || undefined);
      const subjectsToCreate = selectedSubjectIds.length > 0 ? selectedSubjectIds : [1];

      await Promise.allSettled(
        subjectsToCreate.map((subjectId) =>
          profileService.createLearningGoal({
            subjectId,
            currentLevel: 1,
            desiredLevel: 4,
            goalType: resolvedGoalType,
            deadline: goalDeadline,
            mode: learningMode,
            area: goalArea,
            budgetMin: 150000,
            budgetMax: 300000,
            availabilitySlots: [
              { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
              { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
            ],
          })
        )
      );

      showToast("Thiết lập hồ sơ học tập thành công! Chào mừng bạn đến với EduFit.", "success");
      await refreshUser?.();
      router.push("/student/dashboard");
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Có lỗi xảy ra khi hoàn tất thiết lập";
      showToast(message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  const gradeDescriptions: Record<string, string> = {
    "Lớp 6": "Làm quen phương pháp học THCS và củng cố căn bản",
    "Lớp 7": "Học đúng chương trình THCS & bồi dưỡng tư duy",
    "Lớp 8": "Chuẩn bị kiến thức nền tảng cho năm cuối cấp",
    "Lớp 9": "Chuẩn bị cho mục tiêu thi tuyển sinh vào lớp 10",
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center font-sans">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="size-8 text-amber-500 animate-spin" />
          <span className="text-xs text-neutral-500 font-medium">Đang chuẩn bị lộ trình học tập...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-stone-50/80 flex flex-col justify-between font-sans text-neutral-900 selection:bg-amber-100">
      {/* Top Navbar */}
      <header className="w-full bg-white border-b border-stone-200/80 px-6 py-4 flex items-center justify-between">
        <EduFitLogo href="/" />

        <div className="flex items-center gap-3 text-xs text-neutral-500">
          <span>Xin chào, <strong className="text-neutral-800">{user?.fullName || "Học sinh"}</strong></span>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-5xl mx-auto w-full px-4 sm:px-6 py-8 sm:py-12 my-auto">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* CỘT TRÁI: Tiến trình & Lời giới thiệu (4 Cols) */}
          <div className="lg:col-span-4 space-y-6">
            <div>
              <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-amber-50 text-amber-900 rounded-full text-xs font-bold mb-3">
                <Target className="size-3.5 text-amber-600" />
                <span>THIẾT LẬP HỒ SƠ HỌC SINH</span>
              </div>
              <h1 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 tracking-tight leading-tight">
                Hãy để EduFit hiểu bạn hơn một chút.
              </h1>
              <p className="text-xs text-neutral-500 mt-2 leading-relaxed">
                Hoàn tất 2 bước đơn giản để kích hoạt bộ gợi ý gia sư AI Match chuẩn xác nhất cho bạn.
              </p>
            </div>

            {/* Stepper Indicator */}
            <div className="p-4 bg-white rounded-2xl border border-stone-200/90 shadow-2xs space-y-3">
              <div
                className={`flex items-center gap-3 p-2.5 rounded-xl transition-all cursor-pointer ${
                  step === 1 ? "bg-amber-50/80 text-amber-950 font-bold" : "text-neutral-600"
                }`}
                onClick={() => setStep(1)}
              >
                <div
                  className={`size-7 rounded-lg flex items-center justify-center text-xs font-black ${
                    step === 2
                      ? "bg-emerald-500 text-white"
                      : step === 1
                      ? "bg-amber-500 text-white"
                      : "bg-stone-200 text-neutral-600"
                  }`}
                >
                  {step === 2 ? <CheckCircle2 className="size-4" /> : "1"}
                </div>
                <div className="text-xs">
                  <div className="font-bold">Bước 1: Chọn lớp</div>
                  <div className="text-[11px] text-neutral-400 font-normal">Cấp học hiện tại</div>
                </div>
              </div>

              <div
                className={`flex items-center gap-3 p-2.5 rounded-xl transition-all cursor-pointer ${
                  step === 2 ? "bg-amber-50/80 text-amber-950 font-bold" : "text-neutral-600"
                }`}
                onClick={() => {
                  if (selectedLevelId) setStep(2);
                }}
              >
                <div
                  className={`size-7 rounded-lg flex items-center justify-center text-xs font-black ${
                    step === 2 ? "bg-amber-500 text-white" : "bg-stone-200 text-neutral-600"
                  }`}
                >
                  2
                </div>
                <div className="text-xs">
                  <div className="font-bold">Bước 2: Môn quan tâm & Khu vực</div>
                  <div className="text-[11px] text-neutral-400 font-normal">Mục tiêu & Vị trí học</div>
                </div>
              </div>
            </div>

            {/* Hint Box (Figma: Gợi ý nhỏ) */}
            <div className="p-4 bg-amber-50/60 rounded-2xl border border-amber-200/70 flex items-start gap-3">
              <Lightbulb className="size-4 text-amber-600 shrink-0 mt-0.5" />
              <div className="text-xs">
                <span className="font-bold text-amber-900 block mb-0.5">
                  Bạn có thể thay đổi sau
                </span>
                <span className="text-amber-800/90 leading-relaxed block text-[11px]">
                  Lớp học giúp EduFit hiển thị đúng ngân hàng môn học và kết nối các gia sư chuyên môn phù hợp nhất.
                </span>
              </div>
            </div>
          </div>

          {/* CỘT PHẢI: Form Từng Bước (8 Cols) */}
          <div className="lg:col-span-8 bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs">
            {/* ========================================================================= */}
            {/* BƯỚC 1: CHỌN LỚP (Figma 36:1792) */}
            {/* ========================================================================= */}
            {step === 1 && (
              <div className="space-y-6 animate-in fade-in duration-200">
                <div className="flex items-center justify-between pb-4 border-b border-stone-100">
                  <div>
                    <span className="text-[11px] font-bold text-amber-600 uppercase tracking-wider block">
                      BƯỚC 1 / 2
                    </span>
                    <h2 className="font-heading text-xl sm:text-2xl font-bold text-neutral-900 mt-0.5">
                      Bạn đang học lớp nào?
                    </h2>
                    <p className="text-xs text-neutral-500 mt-1">
                      Chọn một lớp để chúng mình cá nhân hóa nội dung và lộ trình học tập.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {educationLevels.map((lvl) => {
                    const isSelected = selectedLevelId === lvl.levelId;
                    const desc =
                      gradeDescriptions[lvl.name] || "Chương trình giáo dục tiêu chuẩn phổ thông";

                    return (
                      <div
                        key={lvl.levelId}
                        onClick={() => setSelectedLevelId(lvl.levelId)}
                        className={`p-5 rounded-2xl border-2 cursor-pointer transition-all flex items-start gap-4 ${
                          isSelected
                            ? "border-amber-500 bg-amber-50/40 shadow-sm shadow-amber-500/10"
                            : "border-stone-200/90 bg-stone-50/40 hover:border-amber-300 hover:bg-white"
                        }`}
                      >
                        <div
                          className={`size-11 rounded-xl flex items-center justify-center font-heading font-black text-base shrink-0 ${
                            isSelected
                              ? "bg-amber-500 text-white shadow-xs"
                              : "bg-white text-neutral-700 border border-stone-200"
                          }`}
                        >
                          {lvl.name.replace(/[^0-9]/g, "") || lvl.levelId}
                        </div>
                        <div className="space-y-1">
                          <div className="flex items-center justify-between">
                            <h3 className="font-bold text-sm text-neutral-900">{lvl.name}</h3>
                            {isSelected && (
                              <CheckCircle2 className="size-4 text-amber-600 shrink-0" />
                            )}
                          </div>
                          <p className="text-[11px] text-neutral-500 leading-relaxed">{desc}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>

                <div className="pt-4 border-t border-stone-100 flex justify-end">
                  <button
                    type="button"
                    onClick={handleNextStep}
                    disabled={!selectedLevelId}
                    className="inline-flex items-center gap-2 px-6 py-3 bg-amber-500 hover:bg-amber-600 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
                  >
                    <span>Tiếp tục</span>
                    <ArrowRight className="size-4" />
                  </button>
                </div>
              </div>
            )}

            {/* ========================================================================= */}
            {/* BƯỚC 2: CHỌN MÔN & KHU VỰC & MỤC TIÊU (Figma 36:1858) */}
            {/* ========================================================================= */}
            {step === 2 && (
              <div className="space-y-6 animate-in fade-in duration-200">
                <div className="flex items-center justify-between pb-4 border-b border-stone-100">
                  <div>
                    <span className="text-[11px] font-bold text-amber-600 uppercase tracking-wider block">
                      BƯỚC 2 / 2
                    </span>
                    <h2 className="font-heading text-xl sm:text-2xl font-bold text-neutral-900 mt-0.5">
                      Bạn quan tâm đến môn học nào?
                    </h2>
                    <p className="text-xs text-neutral-500 mt-1">
                      Chọn một hoặc nhiều môn. Bạn có thể cập nhật lại danh sách này trong Hồ sơ.
                    </p>
                  </div>
                </div>

                {/* 1. Môn học quan tâm */}
                <div className="space-y-2.5">
                  <label className="block text-xs font-bold text-neutral-800">
                    Môn học cần gia sư kèm cặp <span className="text-rose-500">*</span>
                  </label>
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                    {subjectsCatalog.map((sub) => {
                      const isSelected = selectedSubjectIds.includes(sub.subjectId);
                      return (
                        <button
                          key={sub.subjectId}
                          type="button"
                          onClick={() => toggleSubject(sub.subjectId)}
                          className={`p-3 rounded-xl border text-xs font-bold transition-all flex items-center justify-between ${
                            isSelected
                              ? "bg-amber-500 text-white border-amber-500 shadow-xs"
                              : "bg-stone-50 text-neutral-700 border-stone-200 hover:border-amber-300 hover:bg-white"
                          }`}
                        >
                          <span className="flex items-center gap-2">
                            <BookOpen className="size-3.5" />
                            <span>{sub.name}</span>
                          </span>
                          {isSelected && <CheckCircle2 className="size-3.5 text-white shrink-0" />}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* 2. Khu vực cư trú */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-bold text-neutral-800">
                    Khu vực học tập / Nơi cư trú <span className="text-rose-500">*</span>
                  </label>
                  <div className="relative">
                    <MapPin className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      required
                      value={area}
                      onChange={(e) => setArea(e.target.value)}
                      placeholder="VD: Cầu Giấy, Hà Nội hoặc Quận 1, TP.HCM"
                      className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 text-neutral-900 font-medium"
                    />
                  </div>
                  <p className="text-[11px] text-neutral-400">
                    Dùng để ghép nối gia sư dạy kèm trực tiếp gần nhà hoặc cùng khu vực
                  </p>
                </div>

                {/* 3. Hình thức học mong muốn */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-bold text-neutral-800">
                    Hình thức học mong muốn
                  </label>
                  <div className="grid grid-cols-3 gap-3">
                    {[
                      { mode: "ONLINE", label: "Online", icon: Laptop },
                      { mode: "OFFLINE", label: "Offline", icon: Users },
                      { mode: "BOTH", label: "Cả hai", icon: Compass },
                    ].map((item) => (
                      <button
                        key={item.mode}
                        type="button"
                        onClick={() => setLearningMode(item.mode as any)}
                        className={`p-2.5 rounded-xl border text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                          learningMode === item.mode
                            ? "bg-stone-900 text-white border-stone-900 shadow-xs"
                            : "bg-stone-50 text-neutral-700 border-stone-200 hover:bg-white"
                        }`}
                      >
                        <item.icon className="size-3.5" />
                        <span>{item.label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* 4. Mục tiêu học tập cá nhân */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-bold text-neutral-800">
                    Mục tiêu trọng tâm của bạn
                  </label>
                  <input
                    type="text"
                    value={goalTitle}
                    onChange={(e) => setGoalTitle(e.target.value)}
                    placeholder="VD: Luyện thi vào lớp 10 THPT, Cải thiện điểm số môn Toán..."
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 text-neutral-900 font-medium"
                  />
                </div>

                {/* Hành động */}
                <div className="pt-4 border-t border-stone-100 flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => setStep(1)}
                    className="inline-flex items-center gap-1.5 px-4 py-2.5 bg-stone-100 hover:bg-stone-200 text-neutral-700 font-bold text-xs rounded-xl transition-all cursor-pointer"
                  >
                    <ArrowLeft className="size-4" />
                    <span>Quay lại</span>
                  </button>

                  <button
                    type="button"
                    disabled={submitting}
                    onClick={handleComplete}
                    className="inline-flex items-center gap-2 px-6 py-3 bg-amber-500 hover:bg-amber-600 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
                  >
                    {submitting ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <CheckCircle2 className="size-4" />
                    )}
                    <span>{submitting ? "Đang lưu hồ sơ..." : "Hoàn tất thiết lập"}</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full text-center py-4 text-neutral-400 text-[11px] border-t border-stone-200/60">
        © 2026 EduFit Platform · Kiến trúc module theo tài liệu kỹ thuật
      </footer>
    </div>
  );
}
