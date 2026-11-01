"use client";

import React, { useEffect, useState, useMemo } from "react";
import {
  profileService,
  StudentProfileData,
  LearningGoalData,
  LearningGoalType,
  TutorAvailabilitySlot,
} from "@/services/profile";
import { catalogService, EducationLevel, SubjectItem } from "@/services/catalog";
import { useToast } from "@/shared/ui/Toast";
import { AvailabilitySlotPicker } from "./AvailabilitySlotPicker";
import { formatMatchingSlots, formatLocalDateToIso } from "@/shared/lib/dateTime";
import {
  GraduationCap,
  MapPin,
  Loader2,
  Save,
  Sparkles,
  Target,
  Plus,
  Pencil,
  Archive,
  Clock,
  Calendar,
  DollarSign,
  X,
  AlertCircle,
  BookOpen,
} from "lucide-react";

interface StudentProfileFormProps {
  initialProfile?: StudentProfileData;
  onSaved?: () => void;
}

const GOAL_TYPE_OPTIONS: { value: LearningGoalType; label: string }[] = [
  { value: "CONSOLIDATE", label: "Củng cố kiến thức" },
  { value: "IMPROVE_GRADES", label: "Cải thiện điểm số" },
  { value: "ADVANCED_STUDY", label: "Bồi dưỡng nâng cao" },
  { value: "EXAM_PREPARATION", label: "Ôn thi chuyển cấp / thi tuyển" },
  { value: "OTHER", label: "Mục tiêu khác" },
];

function getGoalTypeLabel(type?: string): string {
  switch (type) {
    case "CONSOLIDATE":
      return "Củng cố kiến thức";
    case "IMPROVE_GRADES":
      return "Cải thiện điểm số";
    case "ADVANCED_STUDY":
      return "Bồi dưỡng nâng cao";
    case "EXAM_PREPARATION":
      return "Ôn thi chuyển cấp";
    case "OTHER":
      return "Mục tiêu khác";
    default:
      return type || "";
  }
}

function getModeLabel(mode?: string): string {
  switch (mode) {
    case "ONLINE":
      return "Trực tuyến (Online)";
    case "OFFLINE":
      return "Tại nhà (Offline)";
    case "BOTH":
      return "Linh hoạt (Online & Offline)";
    default:
      return mode || "";
  }
}

export function StudentProfileForm({ initialProfile, onSaved }: StudentProfileFormProps) {
  const { showToast } = useToast();

  const [loading, setLoading] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [educationLevels, setEducationLevels] = useState<EducationLevel[]>([]);
  const [subjectsCatalog, setSubjectsCatalog] = useState<SubjectItem[]>([]);
  const [goals, setGoals] = useState<LearningGoalData[]>([]);

  // Form State: Khối lớp & Khu vực học sinh
  const [selectedLevelId, setSelectedLevelId] = useState<number | undefined>(
    initialProfile?.educationLevelId
  );
  const [area, setArea] = useState<string>(initialProfile?.area || "");

  // Modal / Drawer state for creating or editing goals
  const [isAddingGoal, setIsAddingGoal] = useState(false);
  const [editingGoal, setEditingGoal] = useState<LearningGoalData | null>(null);

  // Goal Form Fields
  const [formSubjectId, setFormSubjectId] = useState<number>(1);
  const [formGoalType, setFormGoalType] = useState<LearningGoalType>("IMPROVE_GRADES");
  const [formCurrentLevel, setFormCurrentLevel] = useState<number>(1);
  const [formDesiredLevel, setFormDesiredLevel] = useState<number>(4);
  const [formMode, setFormMode] = useState<"ONLINE" | "OFFLINE" | "BOTH">("BOTH");
  const [formArea, setFormArea] = useState<string>("");
  const [formBudgetMin, setFormBudgetMin] = useState<number>(150000);
  const [formBudgetMax, setFormBudgetMax] = useState<number>(300000);
  const [formDeadline, setFormDeadline] = useState<string>("");
  const [formSlots, setFormSlots] = useState<TutorAvailabilitySlot[]>([]);
  const [savingGoal, setSavingGoal] = useState(false);
  const [goalFormError, setGoalFormError] = useState<string | null>(null);

  // Load initial profile, catalogs, and learning goals
  const loadData = async () => {
    try {
      setLoading(true);
      const [levels, subjects, currentProfile, myGoals] = await Promise.all([
        catalogService.getEducationLevels().catch(() => []),
        catalogService.getSubjects().catch(() => []),
        initialProfile ? Promise.resolve(initialProfile) : profileService.getMyStudentProfile().catch(() => null),
        profileService.getMyLearningGoals().catch(() => []),
      ]);

      setEducationLevels(levels);
      setSubjectsCatalog(subjects);

      if (currentProfile) {
        setSelectedLevelId(currentProfile.educationLevelId);
        setArea(currentProfile.area || "");
      }

      setGoals(myGoals.filter((g) => g.status === "ACTIVE"));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Lỗi khi tải dữ liệu hồ sơ học sinh";
      showToast(msg, "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialProfile]);

  const activeGoals = useMemo(() => goals.filter((g) => g.status === "ACTIVE"), [goals]);

  // Unused subjects for new goal
  const unusedSubjects = useMemo(() => {
    const usedSubjectIds = new Set(activeGoals.map((g) => g.subjectId));
    return subjectsCatalog.filter((s) => !usedSubjectIds.has(s.subjectId));
  }, [subjectsCatalog, activeGoals]);

  // 1. Submit Profile (Education Level & Area)
  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);

    try {
      await profileService.updateStudentProfile({
        educationLevelId: selectedLevelId,
        area: area.trim() || undefined,
      });
      showToast("Cập nhật thông tin khối lớp và khu vực thành công!", "success");
      onSaved?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu hồ sơ";
      showToast(msg, "error");
    } finally {
      setSavingProfile(false);
    }
  };

  // Open Create Goal Modal
  const openCreateGoalModal = () => {
    const defaultSubject = unusedSubjects[0] || subjectsCatalog[0] || { subjectId: 1 };
    setFormSubjectId(defaultSubject.subjectId);
    setFormGoalType("IMPROVE_GRADES");
    setFormCurrentLevel(1);
    setFormDesiredLevel(4);
    setFormMode("BOTH");
    setFormArea(area.trim() || "");
    setFormBudgetMin(150000);
    setFormBudgetMax(300000);
    const sixMonthsLater = new Date();
    sixMonthsLater.setMonth(sixMonthsLater.getMonth() + 6);
    setFormDeadline(formatLocalDateToIso(sixMonthsLater));
    setFormSlots([
      { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
    ]);
    setGoalFormError(null);
    setIsAddingGoal(true);
  };

  // Open Edit Goal Modal
  const openEditGoalModal = (goal: LearningGoalData) => {
    setEditingGoal(goal);
    setFormSubjectId(goal.subjectId);
    setFormGoalType(goal.goalType);
    setFormCurrentLevel(goal.currentLevel || 1);
    setFormDesiredLevel(goal.desiredLevel || 4);
    setFormMode(goal.mode || "BOTH");
    setFormArea(goal.area || area.trim() || "");
    setFormBudgetMin(goal.budgetMin || 150000);
    setFormBudgetMax(goal.budgetMax || 300000);
    setFormDeadline(goal.deadline ? goal.deadline.slice(0, 10) : "");
    setFormSlots(goal.availabilitySlots || []);
    setGoalFormError(null);
  };

  // Close Goal Modal
  const closeGoalModal = () => {
    setIsAddingGoal(false);
    setEditingGoal(null);
    setGoalFormError(null);
  };

  // Save Goal (Create or Edit)
  const handleSaveGoal = async (e: React.FormEvent) => {
    e.preventDefault();
    setGoalFormError(null);

    if (formMode !== "ONLINE" && !formArea.trim()) {
      const msg = "Vui lòng nhập khu vực học cho hình thức Offline hoặc Cả hai.";
      setGoalFormError(msg);
      return;
    }

    if (!editingGoal && activeGoals.some((g) => g.subjectId === formSubjectId)) {
      const msg = "Bạn đã có một mục tiêu học tập đang hoạt động cho môn học này. Hãy chỉnh sửa mục tiêu hiện tại hoặc lưu trữ mục tiêu cũ.";
      setGoalFormError(msg);
      return;
    }

    if (formDesiredLevel <= formCurrentLevel) {
      const msg = "Mức độ kỳ vọng (kết quả mong muốn) phải lớn hơn năng lực hiện tại.";
      setGoalFormError(msg);
      return;
    }

    if (!formDeadline) {
      const msg = "Vui lòng chọn thời hạn hoàn thành mục tiêu.";
      setGoalFormError(msg);
      return;
    }

    const todayStr = new Date().toISOString().split("T")[0];
    if (formDeadline <= todayStr) {
      const msg = "Hạn hoàn thành mục tiêu phải là ngày trong tương lai.";
      setGoalFormError(msg);
      return;
    }

    if (formBudgetMax < 50000) {
      const msg = "Ngân sách tối đa phải từ 50.000 đ/buổi.";
      setGoalFormError(msg);
      return;
    }

    if (formSlots.length === 0) {
      const msg = "Vui lòng chọn ít nhất 1 khung giờ rảnh trong tuần để xếp lịch học.";
      setGoalFormError(msg);
      return;
    }

    const payload = {
      subjectId: formSubjectId,
      currentLevel: formCurrentLevel,
      desiredLevel: formDesiredLevel,
      goalType: formGoalType,
      deadline: formDeadline,
      mode: formMode,
      area: formMode === "ONLINE" ? undefined : formArea.trim(),
      budgetMin: formBudgetMin,
      budgetMax: formBudgetMax,
      availabilitySlots: formSlots.map((s) => ({
        dayOfWeek: s.dayOfWeek,
        startTime: s.startTime,
        endTime: s.endTime,
      })),
    };

    setSavingGoal(true);
    try {
      if (editingGoal) {
        await profileService.updateLearningGoal(editingGoal.goalId, payload);
        showToast("Cập nhật mục tiêu học tập thành công!", "success");
      } else {
        await profileService.createLearningGoal(payload);
        showToast("Tạo mục tiêu học tập mới thành công!", "success");
      }

      // Refresh goals from backend
      const refreshedGoals = await profileService.getMyLearningGoals();
      setGoals(refreshedGoals.filter((g) => g.status === "ACTIVE"));
      closeGoalModal();
      onSaved?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu mục tiêu học tập";
      setGoalFormError(msg);
      showToast(msg, "error");
    } finally {
      setSavingGoal(false);
    }
  };

  // Archive Goal
  const handleArchiveGoal = async (goal: LearningGoalData) => {
    const confirmed = window.confirm(
      `Bạn có chắc chắn muốn lưu trữ mục tiêu môn "${goal.subjectName}"? Khi lưu trữ, mục tiêu sẽ không còn xuất hiện trong danh sách ghép nối.`
    );
    if (!confirmed) return;

    try {
      await profileService.archiveLearningGoal(goal.goalId);
      showToast(`Đã lưu trữ mục tiêu môn ${goal.subjectName}`, "success");
      const refreshedGoals = await profileService.getMyLearningGoals();
      setGoals(refreshedGoals.filter((g) => g.status === "ACTIVE"));
      onSaved?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Không thể lưu trữ mục tiêu";
      showToast(msg, "error");
    }
  };

  if (loading) {
    return (
      <div className="p-12 flex flex-col items-center justify-center gap-3 bg-white rounded-3xl border border-stone-200">
        <Loader2 className="size-6 text-amber-500 animate-spin" />
        <span className="text-xs text-neutral-500 font-medium">Đang tải thông tin hồ sơ học viên...</span>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* 1. KHỐI LỚP & KHU VỰC HỌC TẬP */}
      <form
        onSubmit={handleSaveProfile}
        className="space-y-6 bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-stone-100">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-amber-50 text-amber-900 rounded-full text-xs font-bold mb-2">
              <GraduationCap className="size-3.5 text-amber-600" />
              <span>Hồ Sơ Học Tập (Student Profile)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-neutral-900">
              Thông tin khối lớp & Khu vực học
            </h3>
            <p className="text-xs text-neutral-500 mt-0.5">
              Giúp hệ thống AI Match gợi ý gia sư chính xác theo khối học và vị trí gần bạn
            </p>
          </div>

          <button
            type="submit"
            disabled={savingProfile}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-amber-500 hover:bg-amber-600 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
          >
            {savingProfile ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            <span>{savingProfile ? "Đang lưu..." : "Lưu thông tin khối lớp"}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Chọn Khối lớp (Education Level) */}
          <div className="space-y-2">
            <label className="block text-xs font-bold text-neutral-700">
              Khối lớp hiện tại (Cấp học) <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <select
                value={selectedLevelId || ""}
                onChange={(e) => setSelectedLevelId(e.target.value ? Number(e.target.value) : undefined)}
                className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 text-neutral-900 font-medium transition-all"
              >
                <option value="">-- Chọn khối lớp theo học --</option>
                {educationLevels.map((lvl) => (
                  <option key={lvl.levelId} value={lvl.levelId}>
                    {lvl.name}
                  </option>
                ))}
              </select>
            </div>
            <p className="text-[11px] text-neutral-400">
              Dữ liệu đồng bộ trực tiếp từ danh mục quốc gia của EduFit
            </p>
          </div>

          {/* Nhập Khu vực học (Area) */}
          <div className="space-y-2">
            <label className="block text-xs font-bold text-neutral-700">
              Khu vực / Quận Huyện sinh sống
            </label>
            <div className="relative">
              <MapPin className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={area}
                onChange={(e) => setArea(e.target.value)}
                placeholder="VD: Cầu Giấy, Hà Nội hoặc Quận 1, TP.HCM"
                maxLength={100}
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 text-neutral-900 placeholder:text-neutral-400 transition-all"
              />
            </div>
            <p className="text-[11px] text-neutral-400">
              Tối đa 100 ký tự (Dùng khi học viên muốn tìm gia sư dạy kèm tại nhà)
            </p>
          </div>
        </div>
      </form>

      {/* 2. QUẢN LÝ MỤC TIÊU HỌC TẬP THEO MÔN HỌC (LEARNING GOALS) */}
      <div className="bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-stone-100">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-sky-50 text-sky-900 rounded-full text-xs font-bold mb-2">
              <Target className="size-3.5 text-sky-600" />
              <span>Mục Tiêu Môn Học (Learning Goals)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-neutral-900">
              Kế hoạch & Mục tiêu theo từng môn học
            </h3>
            <p className="text-xs text-neutral-500 mt-0.5">
              Mỗi môn học có thể thiết lập hình thức, ngân sách, thời hạn và lịch rảnh riêng biệt
            </p>
          </div>

          <button
            type="button"
            onClick={openCreateGoalModal}
            className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-sky-600 hover:bg-sky-700 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all shrink-0 cursor-pointer"
          >
            <Plus className="size-4" />
            <span>Thêm mục tiêu môn học</span>
          </button>
        </div>

        {activeGoals.length === 0 ? (
          <div className="py-12 px-6 flex flex-col items-center justify-center text-center border-2 border-dashed border-stone-200 rounded-2xl bg-stone-50/50">
            <BookOpen className="size-10 text-stone-400 mb-3" />
            <h4 className="text-sm font-bold text-neutral-800 mb-1">
              Chưa có mục tiêu môn học nào đang hoạt động
            </h4>
            <p className="text-xs text-neutral-500 max-w-md mb-4">
              Hãy tạo mục tiêu cho từng môn bạn muốn cải thiện để hệ thống đề xuất gia sư phù hợp nhất với bạn.
            </p>
            <button
              type="button"
              onClick={openCreateGoalModal}
              className="inline-flex items-center gap-2 px-4 py-2 bg-sky-600 hover:bg-sky-700 text-white font-bold text-xs rounded-xl transition-all cursor-pointer"
            >
              <Plus className="size-4" />
              <span>Tạo mục tiêu đầu tiên</span>
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {activeGoals.map((g) => (
              <div
                key={g.goalId}
                className="p-5 rounded-2xl border border-stone-200 bg-white hover:border-sky-300 transition-all shadow-xs flex flex-col justify-between space-y-4"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-heading text-lg font-bold text-neutral-900">
                          {g.subjectName || `Môn học #${g.subjectId}`}
                        </span>
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-stone-100 text-neutral-600">
                          {g.educationLevelName || "THCS"}
                        </span>
                      </div>
                      <p className="text-xs font-semibold text-sky-700 mt-0.5">
                        {getGoalTypeLabel(g.goalType)}
                      </p>
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => openEditGoalModal(g)}
                        title="Chỉnh sửa mục tiêu"
                        className="p-2 text-stone-500 hover:text-sky-600 hover:bg-sky-50 rounded-lg transition-colors cursor-pointer"
                      >
                        <Pencil className="size-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleArchiveGoal(g)}
                        title="Lưu trữ mục tiêu"
                        className="p-2 text-stone-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                      >
                        <Archive className="size-3.5" />
                      </button>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-1">
                    <div className="flex items-center gap-1.5 text-neutral-600">
                      <MapPin className="size-3.5 text-neutral-400 shrink-0" />
                      <span className="truncate">
                        {getModeLabel(g.mode)}
                        {g.area && g.mode !== "ONLINE" ? ` (${g.area})` : ""}
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5 text-neutral-600">
                      <DollarSign className="size-3.5 text-neutral-400 shrink-0" />
                      <span>
                        Tối đa: <strong className="text-neutral-900">{Number(g.budgetMax).toLocaleString("vi-VN")} đ/b</strong>
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5 text-neutral-600">
                      <Calendar className="size-3.5 text-neutral-400 shrink-0" />
                      <span>
                        Hạn: {g.deadline ? new Date(g.deadline).toLocaleDateString("vi-VN") : "Chưa xác định"}
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5 text-neutral-600 sm:col-span-2">
                      <Clock className="size-3.5 text-neutral-400 shrink-0" />
                      <span className="truncate" title={formatMatchingSlots(g.availabilitySlots)}>
                        Lịch rảnh: {formatMatchingSlots(g.availabilitySlots)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="pt-3 border-t border-stone-100 flex items-center justify-between text-[11px]">
                  <span className="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full font-medium inline-flex items-center gap-1">
                    <span className="size-1.5 rounded-full bg-emerald-500" />
                    Đang hoạt động & Ghép gia sư
                  </span>
                  <span className="text-neutral-400">
                    Mức: {g.currentLevel || 1} → {g.desiredLevel || 4}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="p-4 rounded-2xl bg-stone-50 border border-stone-200/80 flex items-start gap-3">
          <Sparkles className="size-4.5 text-sky-500 shrink-0 mt-0.5" />
          <div className="text-xs space-y-1">
            <p className="font-bold text-neutral-800">Cơ chế đa môn học độc lập</p>
            <p className="text-neutral-500 leading-relaxed">
              Bạn có thể tạo nhiều mục tiêu học tập khác nhau. Khi kết nối hoặc học kèm với cùng một gia sư, các lớp học theo từng môn sẽ được quản lý riêng rẽ và minh bạch.
            </p>
          </div>
        </div>
      </div>

      {/* 3. MODAL TẠO HOẶC CHỈNH SỬA MỤC TIÊU HỌC TẬP */}
      {(isAddingGoal || editingGoal) && (
        <div className="fixed inset-0 z-50 bg-neutral-950/60 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-2xl w-full p-6 sm:p-8 space-y-6 shadow-2xl border border-stone-200 my-auto">
            <div className="flex items-center justify-between pb-4 border-b border-stone-100">
              <div>
                <h3 className="font-heading text-lg font-bold text-neutral-900">
                  {editingGoal ? `Chỉnh sửa mục tiêu: ${editingGoal.subjectName}` : "Thêm mục tiêu môn học mới"}
                </h3>
                <p className="text-xs text-neutral-500 mt-0.5">
                  {editingGoal
                    ? "Cập nhật yêu cầu, lịch rảnh và mức ngân sách cho môn học này"
                    : "Thiết lập kế hoạch học tập mới để hệ thống gợi ý gia sư chính xác"}
                </p>
              </div>

              <button
                type="button"
                onClick={closeGoalModal}
                disabled={savingGoal}
                className="p-2 text-stone-400 hover:text-stone-700 rounded-xl transition-colors cursor-pointer"
              >
                <X className="size-5" />
              </button>
            </div>

            {goalFormError && (
              <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 flex items-start gap-2">
                <AlertCircle className="size-4 text-rose-500 shrink-0 mt-0.5" />
                <span>{goalFormError}</span>
              </div>
            )}

            <form onSubmit={handleSaveGoal} className="space-y-5">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Chọn môn học */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Môn học <span className="text-rose-500">*</span>
                  </label>
                  {editingGoal ? (
                    <div className="px-3.5 py-2.5 text-xs bg-stone-100 border border-stone-200 rounded-xl text-neutral-700 font-bold">
                      {editingGoal.subjectName} (Môn học cố định)
                    </div>
                  ) : (
                    <select
                      value={formSubjectId}
                      onChange={(e) => setFormSubjectId(Number(e.target.value))}
                      className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                    >
                      {subjectsCatalog.map((s) => {
                        const isUsed = activeGoals.some((g) => g.subjectId === s.subjectId);
                        return (
                          <option key={s.subjectId} value={s.subjectId} disabled={isUsed}>
                            {s.name} {isUsed ? "(Đã có mục tiêu hoạt động)" : ""}
                          </option>
                        );
                      })}
                    </select>
                  )}
                </div>

                {/* Phân loại mục tiêu */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Loại mục tiêu <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formGoalType}
                    onChange={(e) => setFormGoalType(e.target.value as LearningGoalType)}
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  >
                    {GOAL_TYPE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Hình thức học */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Hình thức học
                  </label>
                  <select
                    value={formMode}
                    onChange={(e) => setFormMode(e.target.value as "ONLINE" | "OFFLINE" | "BOTH")}
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  >
                    <option value="BOTH">Linh hoạt (Online & Offline)</option>
                    <option value="ONLINE">Trực tuyến (Online)</option>
                    <option value="OFFLINE">Tại nhà (Offline)</option>
                  </select>
                </div>

                {/* Khu vực (nếu offline) */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Khu vực học {formMode !== "ONLINE" && <span className="text-rose-500">*</span>}
                  </label>
                  <input
                    type="text"
                    value={formArea}
                    onChange={(e) => setFormArea(e.target.value)}
                    placeholder="VD: Cầu Giấy, Hà Nội"
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  />
                </div>

                {/* Ngân sách tối đa */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Ngân sách tối đa (đ/buổi) <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="number"
                    min={50000}
                    step={10000}
                    value={formBudgetMax}
                    onChange={(e) => setFormBudgetMax(Number(e.target.value))}
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  />
                </div>

                {/* Hạn hoàn thành */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Hạn hoàn thành mục tiêu <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="date"
                    value={formDeadline}
                    onChange={(e) => setFormDeadline(e.target.value)}
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  />
                </div>

                {/* Năng lực hiện tại */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Năng lực hiện tại (1 - 5)
                  </label>
                  <select
                    value={formCurrentLevel}
                    onChange={(e) => setFormCurrentLevel(Number(e.target.value))}
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  >
                    <option value={1}>1 - Mất gốc / Cần lấy lại căn bản</option>
                    <option value={2}>2 - Trung bình yếu</option>
                    <option value={3}>3 - Khá</option>
                    <option value={4}>4 - Giỏi</option>
                    <option value={5}>5 - Xuất sắc</option>
                  </select>
                </div>

                {/* Năng lực mong muốn */}
                <div>
                  <label className="block text-xs font-bold text-neutral-700 mb-1.5">
                    Mức độ kỳ vọng (1 - 5)
                  </label>
                  <select
                    value={formDesiredLevel}
                    onChange={(e) => setFormDesiredLevel(Number(e.target.value))}
                    className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-sky-500 font-medium text-neutral-900"
                  >
                    <option value={2}>2 - Nắm chắc căn bản</option>
                    <option value={3}>3 - Nâng lên mức Khá</option>
                    <option value={4}>4 - Đạt mức Giỏi / Điểm 8+</option>
                    <option value={5}>5 - Chuyên / Thủ khoa / Điểm 9.5+</option>
                  </select>
                </div>
              </div>

              {/* Khung giờ rảnh trong tuần */}
              <div className="pt-2">
                <label className="block text-xs font-bold text-neutral-700 mb-2">
                  Khung giờ rảnh trong tuần để học kèm <span className="text-rose-500">*</span>
                </label>
                <div className="bg-stone-50 p-4 rounded-2xl border border-stone-200">
                  <AvailabilitySlotPicker
                    slots={formSlots}
                    onChange={(newSlots) => setFormSlots(newSlots)}
                  />
                </div>
              </div>

              {/* Action buttons */}
              <div className="pt-4 border-t border-stone-100 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={closeGoalModal}
                  disabled={savingGoal}
                  className="px-5 py-2.5 border border-stone-200 hover:bg-stone-50 font-bold text-xs text-neutral-700 rounded-xl transition-colors cursor-pointer"
                >
                  Hủy bỏ
                </button>

                <button
                  type="submit"
                  disabled={savingGoal}
                  className="inline-flex items-center gap-2 px-6 py-2.5 bg-sky-600 hover:bg-sky-700 text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
                >
                  {savingGoal ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                  <span>{savingGoal ? "Đang lưu..." : editingGoal ? "Lưu thay đổi" : "Tạo mục tiêu"}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
