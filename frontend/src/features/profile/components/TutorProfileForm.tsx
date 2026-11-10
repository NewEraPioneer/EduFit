"use client";

import React, { useEffect, useState } from "react";
import {
  profileService,
  TutorProfileData,
  TutorAvailabilitySlot,
  TutorType,
  UpdateTutorProfilePayload,
} from "@/services/profile";
import { ApiError } from "@/lib/api";
import { useLearningCatalog } from "@/features/learning-needs/hooks/useLearningCatalog";
import { TUTOR_TYPE_LABELS, districtsOf } from "@/features/learning-needs/catalogRules";
import { offeringName } from "@/features/discovery/utils/tutorDisplay";
import { AvailabilitySlotPicker } from "./AvailabilitySlotPicker";
import { TutorOfferingManager } from "./TutorOfferingManager";
import {
  normalizeAvailabilityTime,
  sortAvailabilitySlots,
  validateAvailabilitySlots,
} from "./availabilitySlotUtils";
import { useToast } from "@/shared/ui/Toast";
import {
  User,
  MapPin,
  Save,
  Loader2,
  Briefcase,
  Sparkles,
} from "lucide-react";
import { AiBioAssistantModal } from "./AiBioAssistantModal";

/**
 * Hồ sơ gia sư: thông tin chung, loại gia sư, nơi dạy trực tiếp (Tỉnh/thành → Khu vực),
 * gói dạy (môn, lớp, mục tiêu, học phí riêng) và lịch rảnh.
 * - PUT /api/v1/profiles/tutors/me, /availability-slots
 * - POST/PUT/DELETE /api/v1/profiles/tutors/me/offerings
 */

interface TutorProfileFormProps {
  initialProfile?: TutorProfileData;
  onSaved?: () => void;
}

export function TutorProfileForm({ initialProfile, onSaved }: TutorProfileFormProps) {
  const { showToast } = useToast();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showAiModal, setShowAiModal] = useState(false);

  const { catalog } = useLearningCatalog();
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Tutor Profile Form State
  const [profile, setProfile] = useState<TutorProfileData | null>(initialProfile || null);
  const [displayName, setDisplayName] = useState(initialProfile?.displayName || "");
  const [headline, setHeadline] = useState(initialProfile?.headline || "");
  const [bio, setBio] = useState(initialProfile?.bio || "");
  const [teachingMode, setTeachingMode] = useState<"ONLINE" | "OFFLINE" | "BOTH">(
    initialProfile?.teachingMode || "ONLINE"
  );
  const [tutorType, setTutorType] = useState<TutorType | null>(initialProfile?.tutorType ?? null);
  const [provinceId, setProvinceId] = useState<number | null>(initialProfile?.provinceId ?? null);
  const [districtIds, setDistrictIds] = useState<number[]>(initialProfile?.districts.map((d) => d.id) ?? []);
  const [experienceYears, setExperienceYears] = useState<number>(
    initialProfile?.experienceYears || 1
  );
  const [teachingMethod, setTeachingMethod] = useState(initialProfile?.teachingMethod || "");

  // Subjects & Slots State
  const [availabilitySlots, setAvailabilitySlots] = useState<TutorAvailabilitySlot[]>(
    initialProfile?.availabilitySlots || []
  );

  // Tải dữ liệu ban đầu
  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        const currentProfile = initialProfile
          ?? await profileService.getMyTutorProfile().catch(() => null);

        if (currentProfile) {
          setProfile(currentProfile);
          setDisplayName(currentProfile.displayName || "");
          setHeadline(currentProfile.headline || "");
          setBio(currentProfile.bio || "");
          setTeachingMode(currentProfile.teachingMode || "ONLINE");
          setTutorType(currentProfile.tutorType ?? null);
          setProvinceId(currentProfile.provinceId ?? null);
          setDistrictIds(currentProfile.districts.map((d) => d.id));
          setExperienceYears(currentProfile.experienceYears || 1);
          setTeachingMethod(currentProfile.teachingMethod || "");
          setAvailabilitySlots(currentProfile.availabilitySlots || []);
        }
      } catch (err) {
        console.error("Lỗi khi tải hồ sơ gia sư:", err);
      } finally {
        setLoading(false);
      }
    }

    loadData();
  }, [initialProfile]);

  // Lưu thông tin chung & Lịch rảnh
  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();

    const availabilityValidation = validateAvailabilitySlots(availabilitySlots);
    if (!availabilityValidation.valid) {
      showToast(availabilityValidation.message || "Lịch rảnh chưa hợp lệ", "error");
      return;
    }

    setSaving(true);
    setErrors({});

    try {
      const inPerson = teachingMode !== "ONLINE";
      const payload: UpdateTutorProfilePayload = {
        displayName: displayName.trim(),
        headline: headline.trim() || undefined,
        bio: bio.trim() || undefined,
        teachingMode,
        tutorType,
        provinceId: inPerson ? provinceId : null,
        districtIds: inPerson ? districtIds : [],
        experienceYears,
        teachingMethod: teachingMethod.trim() || undefined,
      };

      // 1. Cập nhật hồ sơ gia sư
      const updatedProfile = await profileService.updateTutorProfile(payload);

      // 2. Cập nhật khung giờ rảnh
      const formattedSlots = sortAvailabilitySlots(availabilitySlots).map((s) => ({
        dayOfWeek: s.dayOfWeek,
        startTime: normalizeAvailabilityTime(s.startTime),
        endTime: normalizeAvailabilityTime(s.endTime),
      }));
      await profileService.setAvailabilitySlots(formattedSlots);

      setProfile(updatedProfile);
      showToast("Lưu hồ sơ và lịch rảnh gia sư thành công!", "success");
      onSaved?.();
    } catch (err: unknown) {
      if (err instanceof ApiError && err.details?.length) {
        setErrors(Object.fromEntries(err.details.map((d) => [d.field, d.message])));
      }
      const message = err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu hồ sơ gia sư";
      showToast(message, "error");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="p-12 flex flex-col items-center justify-center gap-3 bg-surface rounded-3xl border border-line">
        <Loader2 className="size-6 text-action-strong animate-spin" />
        <span className="text-xs text-muted font-medium">Đang tải hồ sơ gia sư...</span>
      </div>
    );
  }

  return (
    <form onSubmit={handleSaveProfile} className="space-y-8">
      {/* 1. Phần Thông Tin Cá Nhân & Học Phí */}
      <div className="bg-surface p-5 sm:p-8 rounded-3xl border border-line/90 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-line">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-soft text-brand-strong rounded-full text-xs font-medium mb-2">
              <Briefcase className="size-3.5 text-brand-strong" />
              <span>Hồ Sơ Gia Sư Chuyên Nghiệp</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-ink">
              Thông tin giảng dạy
            </h3>
            <p className="text-xs text-muted mt-0.5">
              Hiển thị trên thẻ gia sư công khai và hồ sơ tìm kiếm của học sinh / phụ huynh
            </p>
          </div>

          <button
            type="submit"
            disabled={saving}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-brand hover:bg-brand-deep active:scale-[0.99] text-white font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
          >
            {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            <span>{saving ? "Đang lưu..." : "Lưu hồ sơ"}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Tên hiển thị (Display Name) <span className="text-danger-strong">*</span>
            </label>
            <div className="relative">
              <User className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                required
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Thầy Nguyễn Văn A"
                maxLength={100}
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Tiêu đề hồ sơ (Headline)
            </label>
            <input
              type="text"
              value={headline}
              onChange={(e) => setHeadline(e.target.value)}
              placeholder="VD: Cử nhân ĐH Sư Phạm Hà Nội · 5 năm kinh nghiệm luyện thi vào 10"
              maxLength={200}
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
            />
          </div>

          <div>
            <label htmlFor="tutor-type" className="block text-xs font-medium text-ink-2 mb-1.5">
              Bạn là
            </label>
            <select
              id="tutor-type"
              value={tutorType ?? ""}
              onChange={(e) => setTutorType((e.target.value || null) as TutorType | null)}
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
            >
              <option value="">-- Chọn --</option>
              {(Object.keys(TUTOR_TYPE_LABELS) as TutorType[]).map((type) => (
                <option key={type} value={type}>{TUTOR_TYPE_LABELS[type]}</option>
              ))}
            </select>
            <p className="mt-1 text-xs text-muted">
              {profile?.tutorTypeVerified
                ? "Đã được xác minh. Đổi loại gia sư sẽ cần xác minh lại."
                : "Sẽ được xác minh cùng giấy tờ ở mục Xác minh hồ sơ."}
            </p>
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Hình thức giảng dạy
            </label>
            <select
              value={teachingMode}
              onChange={(e) => setTeachingMode(e.target.value as "ONLINE" | "OFFLINE" | "BOTH")}
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
            >
              <option value="ONLINE">Học Online (Google Meet / Zoom)</option>
              <option value="OFFLINE">Dạy kèm tại nhà (Offline)</option>
              <option value="BOTH">Cả Online & Offline</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Số năm kinh nghiệm giảng dạy
            </label>
            <input
              type="number"
              min={0}
              max={50}
              value={experienceYears}
              onChange={(e) => setExperienceYears(Number(e.target.value))}
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
            />
          </div>

          {teachingMode !== "ONLINE" && (
            <div className="md:col-span-2 grid grid-cols-1 md:grid-cols-2 gap-5">
              <div>
                <label htmlFor="tutor-province" className="block text-xs font-medium text-ink-2 mb-1.5">
                  Tỉnh/thành dạy trực tiếp <span className="text-danger-strong">*</span>
                </label>
                <div className="relative">
                  <MapPin className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <select
                    id="tutor-province"
                    value={provinceId ?? ""}
                    onChange={(e) => { setProvinceId(e.target.value ? Number(e.target.value) : null); setDistrictIds([]); }}
                    className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
                  >
                    <option value="">-- Chọn tỉnh/thành --</option>
                    {catalog?.provinces.map((province) => (
                      <option key={province.provinceId} value={province.provinceId}>{province.name}</option>
                    ))}
                  </select>
                </div>
                {errors.provinceId && <p role="alert" className="mt-1 text-xs text-danger-strong">{errors.provinceId}</p>}
              </div>
              {catalog && districtsOf(catalog, provinceId).length > 0 && (
                <fieldset>
                  <legend className="block text-xs font-medium text-ink-2 mb-1.5">Khu vực nhận dạy (bỏ trống = toàn tỉnh/thành)</legend>
                  <div className="flex flex-wrap gap-1.5 max-h-36 overflow-y-auto">
                    {districtsOf(catalog, provinceId).map((district) => {
                      const active = districtIds.includes(district.districtId);
                      return (
                        <button key={district.districtId} type="button" aria-pressed={active}
                          onClick={() => setDistrictIds((prev) => active
                            ? prev.filter((id) => id !== district.districtId)
                            : [...prev, district.districtId])}
                          className={`px-2.5 py-1 text-xs rounded-lg border transition-colors ${
                            active ? "bg-brand-soft text-brand-strong font-medium border-brand-line" : "bg-paper border-line text-ink-2 hover:bg-sunken"
                          }`}>
                          {district.name}
                        </button>
                      );
                    })}
                  </div>
                  {errors.districtIds && <p role="alert" className="mt-1 text-xs text-danger-strong">{errors.districtIds}</p>}
                </fieldset>
              )}
            </div>
          )}
        </div>

        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="block text-xs font-medium text-ink-2">
              Tiểu sử & Giới thiệu bản thân (Bio)
            </label>
            <div className="flex items-center gap-2.5">
              <span className="text-xs text-muted">
                {bio.length} / 1.500 ký tự
              </span>
              <button
                type="button"
                onClick={() => setShowAiModal(true)}
                className="inline-flex items-center gap-1.5 px-3 py-1 bg-action-soft hover:from-action-soft hover:to-action-soft text-action-strong font-medium text-xs rounded-full transition-all border border-action-line/80 shadow-2xs cursor-pointer active:scale-95"
              >
                <Sparkles className="size-3 text-action-strong animate-pulse" />
                <span>Viết bio bằng AI</span>
              </button>
            </div>
          </div>
          <textarea
            rows={4}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="Chia sẻ về phong cách giảng dạy, phương pháp sư phạm và thành tích học viên trước đây..."
            className="w-full p-3.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium leading-relaxed"
          />
        </div>
      </div>

      {/* 2. Gói dạy */}
      <div className="bg-surface p-5 sm:p-8 rounded-3xl border border-line/90 shadow-xs">
        <TutorOfferingManager offerings={profile?.offerings ?? []} onChange={setProfile} />
      </div>

      {/* 3. Lịch rảnh định kỳ của gia sư */}
      <div className="bg-surface p-6 sm:p-8 rounded-3xl border border-line/90 shadow-xs">
        <AvailabilitySlotPicker slots={availabilitySlots} onChange={setAvailabilitySlots} />
      </div>

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
    </form>
  );
}
