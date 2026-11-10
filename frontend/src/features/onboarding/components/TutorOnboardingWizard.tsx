"use client";

import React, { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import { useAuth } from "@/context/AuthContext";
import { markOnboardingDone, sanitizeReturnUrl } from "@/features/auth/utils/authRedirect";
import { useToast } from "@/shared/ui/Toast";
import { profileService, type TutorProfileData, type TutorType } from "@/services/profile";
import { verificationService } from "@/services/verification";
import { useLearningCatalog } from "@/features/learning-needs/hooks/useLearningCatalog";
import { TUTOR_TYPE_LABELS, districtsOf } from "@/features/learning-needs/catalogRules";
import { TutorOfferingManager } from "@/features/profile/components/TutorOfferingManager";
import { offeringName } from "@/features/discovery/utils/tutorDisplay";
import {
  ArrowRight,
  ArrowLeft,
  CheckCircle2,
  Sparkles,
  FileText,
  Upload,
  Check,
  ShieldCheck,
  MapPin,
  Clock,
  Loader2,
  Trash2,
} from "lucide-react";
import { AiBioAssistantModal } from "@/features/profile/components/AiBioAssistantModal";

const MAX_CREDENTIAL_FILE_SIZE = 10 * 1024 * 1024;
const ALLOWED_CREDENTIAL_EXTENSIONS = [".pdf", ".jpg", ".jpeg", ".png"];

export function TutorOnboardingWizard() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, isLoading, refreshUser } = useAuth();
  const { showToast } = useToast();

  const safeReturnUrl = sanitizeReturnUrl(searchParams.get("returnUrl"));

  useEffect(() => {
    if (isLoading) return;
    if (!user) {
      showToast("Đăng nhập bằng tài khoản gia sư để thiết lập hồ sơ.", "error");
      router.replace("/login");
      return;
    }
    if (user.role?.toUpperCase() !== "TUTOR") {
      showToast(`Bạn đang đăng nhập với vai trò ${user.role}. Trang này chỉ dành riêng cho Gia sư.`, "error");
      const targetRole = user.role?.toLowerCase() || "student";
      router.replace(`/${targetRole}/dashboard`);
    }
  }, [user, isLoading, router, showToast]);

  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [lastSaved, setLastSaved] = useState<string | null>(null);

  const { catalog } = useLearningCatalog();

  // Form State - Step 1: Thông tin chung & Gói dạy
  const [profile, setProfile] = useState<TutorProfileData | null>(null);
  const [displayName, setDisplayName] = useState(user?.fullName || "");
  const [tutorType, setTutorType] = useState<TutorType | null>(null);
  const [teachingMode, setTeachingMode] = useState<"ONLINE" | "OFFLINE" | "BOTH">("ONLINE");
  const [provinceId, setProvinceId] = useState<number | null>(null);
  const [districtIds, setDistrictIds] = useState<number[]>([]);
  const [experienceYears, setExperienceYears] = useState<number>(0);

  // Form State - Step 2: CV & Bio
  const [headline, setHeadline] = useState("");
  const [bio, setBio] = useState("");
  const [teachingMethod, setTeachingMethod] = useState("");
  const [credentialType, setCredentialType] = useState<
    "DEGREE" | "CERTIFICATE" | "TEACHING_LICENCE" | "STUDENT_CARD" | "OTHER"
  >("DEGREE");
  const [credentialInstitution, setCredentialInstitution] = useState("");
  const [credentialYear, setCredentialYear] = useState<number | undefined>(new Date().getFullYear());
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [credentialOfferingId, setCredentialOfferingId] = useState<string>("");

  // AI Bio Tool state
  const [showAiModal, setShowAiModal] = useState(false);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        const myProfile = await profileService.getMyTutorProfile().catch(() => null);

        if (myProfile) {
          setProfile(myProfile);
          if (myProfile.displayName) setDisplayName(myProfile.displayName);
          if (myProfile.headline) setHeadline(myProfile.headline);
          if (myProfile.bio) setBio(myProfile.bio);
          if (myProfile.teachingMethod) setTeachingMethod(myProfile.teachingMethod);
          if (myProfile.experienceYears) setExperienceYears(myProfile.experienceYears);
          if (myProfile.teachingMode) setTeachingMode(myProfile.teachingMode);
          setTutorType(myProfile.tutorType);
          setProvinceId(myProfile.provinceId);
          setDistrictIds(myProfile.districts.map((d) => d.id));
          // Đã có gói dạy thì chuyển sang Bước 2 để không phải nhập lại
          if (myProfile.offerings.length > 0) {
            setStep(2);
          }
        }
      } catch (err) {
        console.error("Lỗi khi tải dữ liệu onboarding gia sư:", err);
      } finally {
        setLoading(false);
      }
    }

    loadData();
  }, []);

  const inPerson = teachingMode !== "ONLINE";

  /** Thông tin chung của hồ sơ; dạy trực tiếp mới gửi tỉnh/khu vực. */
  const profilePayload = () => ({
    displayName: displayName.trim() || user?.fullName || "Gia sư EduFit",
    headline: headline.trim(),
    bio: bio.trim(),
    teachingMode,
    tutorType,
    provinceId: inPerson ? provinceId : null,
    districtIds: inPerson ? districtIds : [],
    experienceYears: Number(experienceYears) || 0,
    teachingMethod: teachingMethod.trim(),
  });

  /** Lưu thông tin chung (tạo hồ sơ nếu chưa có) để có thể thêm gói dạy. */
  const handleSaveBasics = async () => {
    if (!displayName.trim()) {
      showToast("Nhập họ tên hiển thị.", "error");
      return;
    }
    if (inPerson && !provinceId) {
      showToast("Chọn tỉnh/thành nơi bạn dạy trực tiếp.", "error");
      return;
    }
    try {
      setSubmitting(true);
      setProfile(await profileService.updateTutorProfile(profilePayload()));
      setLastSaved(new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }));
      showToast("Đã lưu thông tin chung. Tiếp theo, thêm gói dạy.", "success");
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu thông tin", "error");
    } finally {
      setSubmitting(false);
    }
  };

  const handleNextFromStep1 = async () => {
    if (!profile) {
      showToast("Lưu thông tin chung trước.", "error");
      return;
    }
    if (profile.offerings.length === 0) {
      showToast("Thêm ít nhất 1 gói dạy.", "error");
      return;
    }
    try {
      setSubmitting(true);
      setProfile(await profileService.updateTutorProfile(profilePayload()));
      setLastSaved(new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }));
      setStep(2);
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu thông tin chuyên môn", "error");
    } finally {
      setSubmitting(false);
    }
  };

  const handleSkipToDashboard = async () => {
    // Lưu nháp thông tin chung nếu đã đủ điều kiện hợp lệ
    if (profile && (!inPerson || provinceId)) {
      try {
        await profileService.updateTutorProfile(profilePayload());
      } catch (err) {
        console.warn("Lưu nháp khi bỏ qua onboarding:", err);
      }
    }

    markOnboardingDone(
      "tutor",
      user?.id,
      JSON.stringify({
        completedAt: new Date().toISOString(),
        skipped: true,
        displayName,
        offeringsCount: profile?.offerings.length ?? 0,
        step: "SKIPPED",
      })
    );
    showToast("Bạn có thể hoàn thiện hồ sơ và gửi duyệt KYC sau, trong Bảng điều khiển.", "info");
    router.push(safeReturnUrl || "/tutor/dashboard");
  };

  const handleNextFromStep2 = () => {
    if (!bio.trim() || bio.trim().length < 30) {
      showToast("Viết phần giới thiệu bản thân tối thiểu 30 ký tự.", "error");
      return;
    }
    if (!credentialInstitution.trim()) {
      showToast("Nhập đơn vị cấp bằng hoặc giấy tờ.", "error");
      return;
    }
    if (selectedFiles.length === 0) {
      showToast("Vui lòng chọn ít nhất một tệp minh chứng thật!", "error");
      return;
    }
    setStep(3);
    setLastSaved(new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }));
  };

  const handleFileSelection = (event: React.ChangeEvent<HTMLInputElement>) => {
    const incomingFiles = Array.from(event.target.files || []);
    const invalidFile = incomingFiles.find((file) => {
      const lowerName = file.name.toLowerCase();
      return !ALLOWED_CREDENTIAL_EXTENSIONS.some((extension) => lowerName.endsWith(extension));
    });
    if (invalidFile) {
      showToast(`Tệp ${invalidFile.name} không đúng định dạng được hỗ trợ.`, "error");
      event.target.value = "";
      return;
    }

    const oversizedFile = incomingFiles.find((file) => file.size > MAX_CREDENTIAL_FILE_SIZE);
    if (oversizedFile) {
      showToast(`Tệp ${oversizedFile.name} vượt quá giới hạn 10 MB.`, "error");
      event.target.value = "";
      return;
    }

    setSelectedFiles((currentFiles) => {
      const knownFiles = new Set(
        currentFiles.map((file) => `${file.name}:${file.size}:${file.lastModified}`)
      );
      return [
        ...currentFiles,
        ...incomingFiles.filter(
          (file) => !knownFiles.has(`${file.name}:${file.size}:${file.lastModified}`)
        ),
      ];
    });
    event.target.value = "";
  };

  const handleRemoveDoc = (index: number) => {
    setSelectedFiles((currentFiles) => currentFiles.filter((_, fileIndex) => fileIndex !== index));
  };

  const handleComplete = async () => {
    setSubmitting(true);
    try {
      // 1. Cập nhật hồ sơ gia sư lên Backend (gói dạy đã được lưu ở Bước 1)
      await profileService.updateTutorProfile(profilePayload());

      // 2. Gửi đúng các tệp người dùng đã chọn. Lỗi phiên hoặc lỗi upload phải được hiển thị,
      // không được nuốt lỗi rồi đánh dấu hồ sơ là đã nộp.
      await verificationService.submitVerification(
        {
          credentials: [
            {
              type: credentialType,
              institution: credentialInstitution.trim(),
              year: credentialYear ? Number(credentialYear) : undefined,
              note: `Hồ sơ đăng ký gia sư với ${profile?.offerings.length ?? 0} gói dạy`,
              fileIndexes: selectedFiles.map((_, index) => index),
              offeringId: credentialOfferingId || null,
            },
          ],
        },
        selectedFiles
      );

      // 3. Lưu trạng thái hoàn tất vào Local Storage theo user.id
      markOnboardingDone(
        "tutor",
        user?.id,
        JSON.stringify({
          completedAt: new Date().toISOString(),
          displayName,
          offeringsCount: profile?.offerings.length ?? 0,
          step: "COMPLETED",
        })
      );

      showToast("Hoàn tất thiết lập hồ sơ gia sư! Hồ sơ đã được gửi để duyệt KYC.", "success");
      await refreshUser?.();
      router.push(safeReturnUrl || "/tutor/dashboard");
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu hồ sơ gia sư";
      showToast(message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  const progressPercent = step === 1 ? 33 : step === 2 ? 66 : 100;

  if (loading) {
    return (
      <div className="min-h-screen bg-paper flex items-center justify-center font-sans">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="size-8 text-action-strong animate-spin" />
          <span className="text-xs text-muted font-medium">Đang tải hồ sơ gia sư...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-paper font-sans flex flex-col justify-between">
      {/* 1. Header Bar */}
      <header className="bg-surface border-b border-line sticky top-0 z-30 px-4 sm:px-8 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <EduFitLogo href="/" />
          <div className="hidden sm:flex items-center gap-2 pl-6 border-l border-line text-xs text-muted">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 bg-action-soft text-action-strong font-medium rounded-full">
              HỒ SƠ GIA SƯ
            </span>
            <span>Hoàn thiện hồ sơ và giới thiệu bản thân</span>
          </div>
        </div>

        <div className="flex items-center gap-4">
          {lastSaved && (
            <div className="hidden md:flex items-center gap-2 text-xs text-muted">
              <Clock className="size-3.5" />
              <span>Đã lưu nháp lúc {lastSaved}</span>
            </div>
          )}
          <button
            type="button"
            onClick={handleSkipToDashboard}
            className="text-xs text-muted hover:text-ink transition-colors cursor-pointer font-medium"
          >
            Bỏ qua để vào sau
          </button>
        </div>
      </header>

      {/* 2. Main Wizard Container */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-4 sm:p-8 space-y-6">
        {/* Progress & Stepper Bar */}
        <div className="bg-surface rounded-3xl p-5 sm:p-6 border border-line/90 shadow-xs space-y-4">
          <div className="flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <span className="font-medium text-ink">Hồ sơ của bạn</span>
              <span className="text-muted">•</span>
              <span className="text-muted">
                {step === 1
                  ? "Nhập chuyên môn và học phí"
                  : step === 2
                  ? "Hoàn thiện CV và bio để gửi xác minh"
                  : "Kiểm tra và gửi xác minh KYC"}
              </span>
            </div>
            <span className="font-medium text-action-strong">{progressPercent}%</span>
          </div>

          <div className="w-full bg-sunken rounded-full h-2 overflow-hidden">
            <div
              className="bg-action h-2 rounded-full transition-all duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>

          <div className="grid grid-cols-3 gap-2 pt-2 border-t border-line text-xs">
            <div
              className={`flex items-center gap-2 ${
                step >= 1 ? "text-action-strong font-medium" : "text-muted"
              }`}
            >
              <div
                className={`size-6 rounded-full flex items-center justify-center text-xs ${
                  step > 1
                    ? "bg-action text-on-action"
                    : step === 1
                    ? "border-2 border-action text-action-strong"
                    : "border border-line-strong text-muted"
                }`}
              >
                {step > 1 ? <Check className="size-3" /> : "1"}
              </div>
              <span className="hidden sm:inline">Thông tin chuyên môn</span>
            </div>

            <div
              className={`flex items-center gap-2 ${
                step >= 2 ? "text-action-strong font-medium" : "text-muted"
              }`}
            >
              <div
                className={`size-6 rounded-full flex items-center justify-center text-xs ${
                  step > 2
                    ? "bg-action text-on-action"
                    : step === 2
                    ? "border-2 border-action text-action-strong"
                    : "border border-line-strong text-muted"
                }`}
              >
                {step > 2 ? <Check className="size-3" /> : "2"}
              </div>
              <span className="hidden sm:inline">CV & giới thiệu</span>
            </div>

            <div
              className={`flex items-center gap-2 ${
                step === 3 ? "text-action-strong font-medium" : "text-muted"
              }`}
            >
              <div
                className={`size-6 rounded-full flex items-center justify-center text-xs ${
                  step === 3
                    ? "border-2 border-action text-action-strong"
                    : "border border-line-strong text-muted"
                }`}
              >
                3
              </div>
              <span className="hidden sm:inline">Gửi xác minh</span>
            </div>
          </div>
        </div>

        {/* STEP 1: THÔNG TIN CHUNG & GÓI DẠY */}
        {step === 1 && (
          <div className="bg-surface rounded-3xl p-5 sm:p-8 border border-line/90 shadow-xs space-y-6">
            <div className="space-y-1">
              <span className="text-xs font-medium text-action-strong">
                BƯỚC 1 / 3
              </span>
              <h2 className="font-heading text-2xl font-bold text-ink">
                Thông tin chung & Gói dạy
              </h2>
              <p className="text-xs text-muted">
                Cho biết bạn là ai, dạy ở đâu, rồi thêm các gói dạy (môn, khối lớp, mục tiêu và học phí riêng).
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              <div className="sm:col-span-2">
                <label htmlFor="onb-display-name" className="block text-xs font-medium text-ink-2 mb-1.5">
                  Họ và tên gia sư hiển thị <span className="text-danger-strong">*</span>
                </label>
                <input
                  id="onb-display-name"
                  type="text"
                  required
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="VD: Cô Nguyễn Hoàng Lan"
                  className="w-full px-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action text-ink font-medium"
                />
              </div>

              <div>
                <label htmlFor="onb-tutor-type" className="block text-xs font-medium text-ink-2 mb-1.5">
                  Bạn là
                </label>
                <select
                  id="onb-tutor-type"
                  value={tutorType ?? ""}
                  onChange={(e) => setTutorType((e.target.value || null) as TutorType | null)}
                  className="w-full px-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action text-ink font-medium"
                >
                  <option value="">-- Chọn --</option>
                  {(Object.keys(TUTOR_TYPE_LABELS) as TutorType[]).map((type) => (
                    <option key={type} value={type}>{TUTOR_TYPE_LABELS[type]}</option>
                  ))}
                </select>
              </div>

              <div>
                <label htmlFor="onb-experience" className="block text-xs font-medium text-ink-2 mb-1.5">
                  Số năm kinh nghiệm gia sư
                </label>
                <input
                  id="onb-experience"
                  type="number"
                  min={0}
                  max={50}
                  value={experienceYears}
                  onChange={(e) => setExperienceYears(Number(e.target.value))}
                  className="w-full px-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action text-ink font-medium"
                />
              </div>

              <div className="sm:col-span-2">
                <span className="block text-xs font-medium text-ink-2 mb-1.5">Hình thức giảng dạy</span>
                <div className="grid grid-cols-3 gap-2" role="group" aria-label="Hình thức giảng dạy">
                  {[
                    { id: "ONLINE", label: "Online" },
                    { id: "OFFLINE", label: "Trực tiếp" },
                    { id: "BOTH", label: "Cả hai" },
                  ].map((mode) => (
                    <button
                      key={mode.id}
                      type="button"
                      aria-pressed={teachingMode === mode.id}
                      onClick={() => setTeachingMode(mode.id as "ONLINE" | "OFFLINE" | "BOTH")}
                      className={`py-2 text-xs font-medium rounded-xl border text-center transition-all cursor-pointer ${
                        teachingMode === mode.id
                          ? "border-action bg-action-soft text-action-strong"
                          : "border-line hover:bg-paper text-ink-2"
                      }`}
                    >
                      {mode.label}
                    </button>
                  ))}
                </div>
              </div>

              {inPerson && (
                <div className="sm:col-span-2 space-y-3">
                  <div>
                    <label htmlFor="onb-province" className="block text-xs font-medium text-ink-2 mb-1.5">
                      Tỉnh/thành dạy trực tiếp <span className="text-danger-strong">*</span>
                    </label>
                    <div className="relative">
                      <MapPin className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                      <select
                        id="onb-province"
                        value={provinceId ?? ""}
                        onChange={(e) => { setProvinceId(e.target.value ? Number(e.target.value) : null); setDistrictIds([]); }}
                        className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action text-ink font-medium"
                      >
                        <option value="">-- Chọn tỉnh/thành --</option>
                        {catalog?.provinces.map((province) => (
                          <option key={province.provinceId} value={province.provinceId}>{province.name}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  {catalog && districtsOf(catalog, provinceId).length > 0 && (
                    <fieldset>
                      <legend className="block text-xs font-medium text-ink-2 mb-1.5">
                        Khu vực nhận dạy (bỏ trống = toàn tỉnh/thành)
                      </legend>
                      <div className="flex flex-wrap gap-1.5 max-h-36 overflow-y-auto">
                        {districtsOf(catalog, provinceId).map((district) => {
                          const active = districtIds.includes(district.districtId);
                          return (
                            <button
                              key={district.districtId}
                              type="button"
                              aria-pressed={active}
                              onClick={() => setDistrictIds((prev) => active
                                ? prev.filter((id) => id !== district.districtId)
                                : [...prev, district.districtId])}
                              className={`px-2.5 py-1 text-xs rounded-lg transition-all cursor-pointer font-medium ${
                                active
                                  ? "bg-action-soft text-action-strong border border-action-line font-medium"
                                  : "bg-sunken hover:bg-line text-ink-2"
                              }`}
                            >
                              {district.name}
                            </button>
                          );
                        })}
                      </div>
                    </fieldset>
                  )}
                </div>
              )}

              <div className="sm:col-span-2 flex justify-end">
                <button
                  type="button"
                  onClick={handleSaveBasics}
                  disabled={submitting}
                  className="inline-flex items-center gap-2 px-5 py-2 border border-action-line bg-action-soft hover:bg-action-soft text-action-strong font-medium text-xs rounded-xl transition-all disabled:opacity-50 cursor-pointer"
                >
                  {submitting && <Loader2 className="size-4 animate-spin" />}
                  {profile ? "Cập nhật thông tin chung" : "Lưu thông tin chung"}
                </button>
              </div>
            </div>

            <div className="pt-4 border-t border-line">
              {profile ? (
                <TutorOfferingManager offerings={profile.offerings} onChange={setProfile} />
              ) : (
                <p className="text-xs text-muted bg-paper rounded-2xl p-4">
                  Lưu thông tin chung để bắt đầu thêm gói dạy.
                </p>
              )}
            </div>

            <div className="flex items-center justify-end pt-4 border-t border-line">
              <button
                type="button"
                onClick={handleNextFromStep1}
                disabled={submitting || !profile || profile.offerings.length === 0}
                className="inline-flex items-center gap-2 px-6 py-2.5 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
              >
                <span>Tiếp tục</span>
                <ArrowRight className="size-4" />
              </button>
            </div>
          </div>
        )}

        {/* STEP 2: CV, GIỚI THIỆU & PHƯƠNG PHÁP (Figma 36:1990) */}
        {step === 2 && (
          <div className="bg-surface rounded-3xl p-6 sm:p-8 border border-line/90 shadow-xs space-y-6">
            <div className="space-y-1">
              <span className="text-xs font-medium text-action-strong">
                BƯỚC 2 / 3
              </span>
              <h2 className="font-heading text-2xl font-bold text-ink">
                CV, giới thiệu bản thân & phương pháp giảng dạy
              </h2>
              <p className="text-xs text-muted">
                Tải CV, viết bio và mô tả phương pháp giảng dạy. AI sẽ giúp bạn tạo bản nháp giới thiệu bản thân.
              </p>
            </div>

            {/* Tài liệu đính kèm */}
            <div className="space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label htmlFor="credential-type" className="text-xs font-medium text-ink-2">
                    Loại giấy tờ
                  </label>
                  <select
                    id="credential-type"
                    value={credentialType}
                    onChange={(event) => {
                      const newType = event.target.value as typeof credentialType;
                      setCredentialType(newType);
                      if (newType === "OTHER") {
                        setCredentialYear(undefined);
                      } else if (!credentialYear) {
                        setCredentialYear(new Date().getFullYear());
                      }
                    }}
                    className="mt-1.5 w-full px-3 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-action"
                  >
                    <option value="DEGREE">Bằng cấp</option>
                    <option value="CERTIFICATE">Chứng chỉ</option>
                    <option value="TEACHING_LICENCE">Chứng chỉ sư phạm</option>
                    <option value="STUDENT_CARD">Thẻ sinh viên</option>
                    <option value="OTHER">Giấy tờ khác / CCCD</option>
                  </select>
                </div>
                <div className="sm:col-span-2">
                  <label htmlFor="credential-institution" className="text-xs font-medium text-ink-2">
                    Đơn vị cấp
                  </label>
                  <input
                    id="credential-institution"
                    type="text"
                    value={credentialInstitution}
                    onChange={(event) => setCredentialInstitution(event.target.value)}
                    placeholder="Ví dụ: Trường Đại học Sư phạm Hà Nội"
                    maxLength={200}
                    className="mt-1.5 w-full px-3 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-action"
                  />
                </div>
                <div>
                  <label htmlFor="credential-year" className="text-xs font-medium text-ink-2">
                    Năm cấp {credentialType === "OTHER" ? "(Không bắt buộc)" : ""}
                  </label>
                  <input
                    id="credential-year"
                    type="number"
                    min={1950}
                    max={new Date().getFullYear() + 10}
                    value={credentialYear ?? ""}
                    onChange={(event) =>
                      setCredentialYear(event.target.value ? Number(event.target.value) : undefined)
                    }
                    placeholder={credentialType === "OTHER" ? "Để trống nếu là CCCD" : "Ví dụ: 2024"}
                    className="mt-1.5 w-full px-3 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-action"
                  />
                </div>
              </div>

              {profile && profile.offerings.some((offering) => offering.goals.some((goal) => goal.premium)) && (
                <div>
                  <label htmlFor="credential-offering" className="text-xs font-medium text-ink-2">
                    Giấy tờ này chứng minh cho gói dạy (không bắt buộc)
                  </label>
                  <select
                    id="credential-offering"
                    value={credentialOfferingId}
                    onChange={(event) => setCredentialOfferingId(event.target.value)}
                    className="mt-1.5 w-full px-3 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-action"
                  >
                    <option value="">Minh chứng chung</option>
                    {profile.offerings
                      .filter((offering) => offering.goals.some((goal) => goal.premium))
                      .map((offering) => (
                        <option key={offering.offeringId} value={offering.offeringId}>
                          {offeringName(offering)} · {offering.goals.filter((g) => g.premium).map((g) => g.name).join(", ")}
                        </option>
                      ))}
                  </select>
                  <p className="mt-1 text-xs text-muted">
                    Mục tiêu như HSG, IELTS cần minh chứng (thành tích, chứng chỉ) để hiển thị không kèm cảnh báo.
                  </p>
                </div>
              )}

              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-ink-2">
                  Bằng cấp / Minh chứng chuyên môn (có thể tải nhiều tệp)
                </label>
                <span className="text-xs text-muted">PDF, JPG hoặc PNG • Tối đa 10 MB</span>
              </div>

              <div className="space-y-2">
                {selectedFiles.map((file, index) => (
                  <div
                    key={`${file.name}-${file.size}-${file.lastModified}`}
                    className="flex items-center justify-between p-3.5 bg-paper border border-line rounded-2xl text-xs"
                  >
                    <div className="flex items-center gap-3">
                      <div className="size-8 rounded-xl bg-action-soft text-action-strong flex items-center justify-center font-medium">
                        <FileText className="size-4" />
                      </div>
                      <div>
                        <p className="font-medium text-ink">{file.name}</p>
                        <p className="text-xs text-muted">
                          {(file.size / 1024 / 1024).toFixed(2)} MB • Sẵn sàng tải lên
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => handleRemoveDoc(index)}
                        className="text-muted hover:text-danger-strong p-1"
                        aria-label={`Xóa tệp ${file.name}`}
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  </div>
                ))}

                <label
                  htmlFor="tutor-credential-files"
                  className="w-full py-3 border-2 border-dashed border-line hover:border-action rounded-2xl flex items-center justify-center gap-2 text-xs font-medium text-ink-2 hover:text-action-strong transition-colors bg-paper/50 cursor-pointer"
                >
                  <Upload className="size-4" />
                  <span>Thêm CV hoặc minh chứng khác</span>
                  <input
                    id="tutor-credential-files"
                    type="file"
                    multiple
                    accept=".pdf,.jpg,.jpeg,.png"
                    onChange={handleFileSelection}
                    className="sr-only"
                  />
                </label>
              </div>
            </div>

            {/* Giới thiệu bản thân + Nút AI Assistant */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-ink-2">
                  Giới thiệu bản thân (Bio) <span className="text-danger-strong">*</span>
                </label>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-muted">
                    {bio.length} / 1.500 ký tự
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowAiModal(true)}
                    className="inline-flex items-center gap-1.5 px-3 py-1 bg-action-soft hover:bg-action-soft text-action-strong font-medium text-xs rounded-full transition-colors cursor-pointer"
                  >
                    <Sparkles className="size-3 text-action-strong" />
                    <span>Viết bio với AI từ CV</span>
                  </button>
                </div>
              </div>

              <textarea
                rows={4}
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                placeholder="Mô tả quá trình giảng dạy, thế mạnh chuyên môn và sự tận tâm dành cho học trò..."
                className="w-full p-4 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 text-ink leading-relaxed resize-none font-sans"
              />
            </div>

            {/* Phương pháp giảng dạy */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-ink-2">
                  Phương pháp giảng dạy
                </label>
                <span className="text-xs text-muted">Hiển thị công khai</span>
              </div>
              <textarea
                rows={3}
                value={teachingMethod}
                onChange={(e) => setTeachingMethod(e.target.value)}
                placeholder="Mô tả cách tổ chức buổi học, cách giải thích và hỗ trợ học sinh tiến bộ..."
                className="w-full p-4 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 text-ink leading-relaxed resize-none font-sans"
              />
              <p className="text-xs text-muted">
                Gợi ý: Mô tả cách tổ chức buổi học, cách phát hiện lỗ hổng kiến thức và động viên học sinh.
              </p>
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-line">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-medium text-ink-2 hover:text-ink cursor-pointer"
              >
                <ArrowLeft className="size-4" />
                <span>Quay lại</span>
              </button>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleSkipToDashboard}
                  className="px-4 py-2.5 text-xs font-medium text-muted hover:text-ink hover:bg-sunken rounded-xl transition-colors cursor-pointer"
                >
                  Bỏ qua bước này
                </button>

                <button
                  type="button"
                  onClick={handleNextFromStep2}
                  className="inline-flex items-center gap-2 px-6 py-2.5 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-xl shadow-xs transition-all cursor-pointer"
                >
                  <span>Lưu và tiếp tục</span>
                  <ArrowRight className="size-4" />
                </button>
              </div>
            </div>
          </div>
        )}

        {/* STEP 3: GỬI XÁC MINH KYC */}
        {step === 3 && (
          <div className="bg-surface rounded-3xl p-6 sm:p-8 border border-line/90 shadow-xs space-y-6">
            <div className="space-y-1">
              <span className="text-xs font-medium text-action-strong">
                BƯỚC 3 / 3
              </span>
              <h2 className="font-heading text-2xl font-bold text-ink">
                Xác thực KYC & Gửi duyệt hồ sơ
              </h2>
              <p className="text-xs text-muted">
                Kiểm tra lại toàn bộ thông tin đã nhập trước khi gửi đến đội ngũ Quản trị viên EduFit kiểm duyệt.
              </p>
            </div>

            {/* Summary Review Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 bg-paper border border-line rounded-2xl space-y-2">
                <span className="text-xs font-medium text-muted">
                  Gói dạy
                </span>
                <p className="text-sm font-medium text-ink">{displayName}</p>
                {tutorType && <p className="text-xs text-ink-2">{TUTOR_TYPE_LABELS[tutorType]}</p>}
                <ul className="space-y-1">
                  {(profile?.offerings ?? []).map((offering) => (
                    <li key={offering.offeringId} className="text-xs text-ink-2 break-words">
                      <strong>{offeringName(offering)}</strong> · {offering.levels.map((l) => l.name).join(", ")} ·{" "}
                      <span className="text-action-strong font-medium">{offering.pricePerSession.toLocaleString("vi-VN")} đ/buổi</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="p-4 bg-paper border border-line rounded-2xl space-y-2">
                <span className="text-xs font-medium text-muted">
                  Địa điểm & Hình thức
                </span>
                <p className="text-xs text-ink-2">
                  Khu vực:{" "}
                  <strong>
                    {!inPerson
                      ? "Toàn quốc (Online)"
                      : [
                          catalog?.districts.filter((d) => districtIds.includes(d.districtId)).map((d) => d.name).join(", "),
                          catalog?.provinces.find((p) => p.provinceId === provinceId)?.name,
                        ].filter(Boolean).join(" · ") || "Chưa chọn"}
                  </strong>
                </p>
                <p className="text-xs text-ink-2">
                  Hình thức:{" "}
                  <strong>
                    {teachingMode === "BOTH"
                      ? "Trực tuyến & Trực tiếp"
                      : teachingMode === "ONLINE"
                      ? "Trực tuyến (Online)"
                      : "Trực tiếp (Tại nhà)"}
                  </strong>
                </p>
                <p className="text-xs text-ink-2">
                  Kinh nghiệm: <strong>{experienceYears} năm</strong>
                </p>
                <p className="text-xs text-ink-2">
                  Minh chứng:{" "}
                  <strong>
                    {credentialType === "DEGREE"
                      ? "Bằng cấp"
                      : credentialType === "CERTIFICATE"
                      ? "Chứng chỉ"
                      : credentialType === "TEACHING_LICENCE"
                      ? "Chứng chỉ sư phạm"
                      : credentialType === "STUDENT_CARD"
                      ? "Thẻ sinh viên"
                      : "Giấy tờ khác / CCCD"}
                  </strong>{" "}
                  ({credentialInstitution || "Chưa nhập"}
                  {credentialYear ? ` - Năm ${credentialYear}` : ""})
                </p>
                <p className="text-xs text-success-strong font-medium flex items-center gap-1">
                  <ShieldCheck className="size-3.5" />
                  Đính kèm {selectedFiles.length} tệp minh chứng
                </p>
              </div>
            </div>

            <div className="p-4 bg-action-soft/60 border border-action-line rounded-2xl space-y-2">
              <div className="flex items-center gap-2 text-xs font-medium text-action-strong">
                <ShieldCheck className="size-4 text-action-strong" />
                <span>Quy trình kiểm duyệt an toàn của EduFit</span>
              </div>
              <p className="text-xs text-action-strong leading-relaxed">
                Sau khi gửi hồ sơ, Admin EduFit sẽ xem xét chứng chỉ và thông tin của bạn trong vòng 24-48 giờ làm việc. Trong thời gian này, bạn vẫn có thể truy cập Bảng điều khiển để cập nhật lịch rảnh và chuẩn bị giáo án.
              </p>
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-line">
              <button
                type="button"
                onClick={() => setStep(2)}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-medium text-ink-2 hover:text-ink cursor-pointer"
              >
                <ArrowLeft className="size-4" />
                <span>Quay lại</span>
              </button>

              <button
                type="button"
                disabled={submitting}
                onClick={handleComplete}
                className="inline-flex items-center gap-2 px-7 py-3 bg-success hover:bg-success-deep active:scale-[0.99] text-white font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
              >
                {submitting ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <CheckCircle2 className="size-4" />
                )}
                <span>{submitting ? "Đang gửi hồ sơ..." : "Hoàn tất & Gửi duyệt KYC"}</span>
              </button>
            </div>
          </div>
        )}
      </main>

      {/* 3. Footer */}
      <footer className="border-t border-line py-4 px-6 text-center text-xs text-muted">
        © 2026 EduFit Platform. Bảo mật thông tin gia sư & cam kết chất lượng giáo dục.
      </footer>

      {/* Modal trợ lý AI viết Bio */}
      <AiBioAssistantModal
        isOpen={showAiModal}
        onClose={() => setShowAiModal(false)}
        displayName={displayName}
        subjects={(profile?.offerings ?? []).map((offering) => offeringName(offering))}
        educationLevels={Array.from(new Set((profile?.offerings ?? []).flatMap((o) => o.levels.map((l) => l.name))))}
        experienceYears={experienceYears}
        currentBio={bio}
        onApplyBio={(newBio) => setBio(newBio)}
      />
    </div>
  );
}
