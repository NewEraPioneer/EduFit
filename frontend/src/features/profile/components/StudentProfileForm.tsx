"use client";

import React, { useEffect, useState } from "react";
import { GraduationCap, Loader2, Save } from "lucide-react";
import { ApiError } from "@/lib/api";
import { profileService, type StudentProfileData } from "@/services/profile";
import { useToast } from "@/shared/ui/Toast";
import { districtsOf } from "@/features/learning-needs/catalogRules";
import { LearningNeedManager } from "@/features/learning-needs/components/LearningNeedManager";
import { useLearningCatalog } from "@/features/learning-needs/hooks/useLearningCatalog";

interface StudentProfileFormProps {
  initialProfile?: StudentProfileData;
  onSaved?: () => void;
}

const selectClass =
  "mt-1.5 w-full px-3.5 py-2.5 text-sm bg-paper border border-line rounded-xl focus:outline-hidden focus:ring-2 focus:ring-action/20";

/**
 * Hồ sơ học sinh chỉ giữ khối lớp và khu vực mặc định (để điền sẵn nhu cầu mới).
 * Mỗi nhu cầu học tự lưu lớp, môn, mục tiêu và điều kiện học riêng.
 */
export function StudentProfileForm({ initialProfile, onSaved }: StudentProfileFormProps) {
  const { showToast } = useToast();
  const { catalog } = useLearningCatalog();
  const [profile, setProfile] = useState<StudentProfileData | undefined>(initialProfile);
  const [levelId, setLevelId] = useState<number | null>(initialProfile?.educationLevelId ?? null);
  const [provinceId, setProvinceId] = useState<number | null>(initialProfile?.provinceId ?? null);
  const [districtId, setDistrictId] = useState<number | null>(initialProfile?.districtId ?? null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (initialProfile) return;
    profileService.getMyStudentProfile().then((loaded) => {
      setProfile(loaded);
      setLevelId(loaded.educationLevelId ?? null);
      setProvinceId(loaded.provinceId ?? null);
      setDistrictId(loaded.districtId ?? null);
    }).catch(() => undefined);
  }, [initialProfile]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setErrors({});
    try {
      const saved = await profileService.updateStudentProfile({ educationLevelId: levelId, provinceId, districtId });
      setProfile(saved);
      showToast("Đã lưu hồ sơ học tập", "success");
      onSaved?.();
    } catch (error) {
      if (error instanceof ApiError && error.details?.length) {
        setErrors(Object.fromEntries(error.details.map((d) => [d.field, d.message])));
      } else {
        showToast(error instanceof Error ? error.message : "Không thể lưu hồ sơ", "error");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-8">
      <form onSubmit={save} className="space-y-6 bg-surface p-5 sm:p-8 rounded-3xl border border-line/90 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-line">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-action-soft text-action-strong rounded-full text-xs font-medium mb-2">
              <GraduationCap className="size-3.5 text-action-strong" />Hồ sơ học tập
            </div>
            <h3 className="font-heading text-xl font-bold text-ink">Khối lớp & khu vực mặc định</h3>
            <p className="text-xs text-muted mt-0.5">Dùng để điền sẵn khi tạo nhu cầu học mới.</p>
          </div>
          <button type="submit" disabled={saving}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-action hover:bg-action-deep text-on-action font-medium text-xs rounded-xl disabled:opacity-50 shrink-0">
            {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            {saving ? "Đang lưu..." : "Lưu hồ sơ"}
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <label className="text-xs font-medium text-ink-2">
            Khối lớp
            <select value={levelId ?? ""} onChange={(e) => setLevelId(e.target.value ? Number(e.target.value) : null)} className={selectClass}>
              <option value="">-- Chọn khối lớp --</option>
              {catalog?.educationLevels.map((l) => <option key={l.levelId} value={l.levelId}>{l.name}</option>)}
            </select>
            {errors.educationLevelId && <span role="alert" className="block mt-1 text-danger-strong font-medium">{errors.educationLevelId}</span>}
          </label>
          <label className="text-xs font-medium text-ink-2">
            Tỉnh/thành
            <select value={provinceId ?? ""} onChange={(e) => { setProvinceId(e.target.value ? Number(e.target.value) : null); setDistrictId(null); }} className={selectClass}>
              <option value="">-- Chưa chọn --</option>
              {catalog?.provinces.map((p) => <option key={p.provinceId} value={p.provinceId}>{p.name}</option>)}
            </select>
            {errors.provinceId && <span role="alert" className="block mt-1 text-danger-strong font-medium">{errors.provinceId}</span>}
          </label>
          <label className="text-xs font-medium text-ink-2">
            Khu vực
            <select value={districtId ?? ""} disabled={!catalog || districtsOf(catalog, provinceId).length === 0}
              onChange={(e) => setDistrictId(e.target.value ? Number(e.target.value) : null)} className={`${selectClass} disabled:opacity-50`}>
              <option value="">-- Chưa chọn --</option>
              {catalog && districtsOf(catalog, provinceId).map((d) => <option key={d.districtId} value={d.districtId}>{d.name}</option>)}
            </select>
            {errors.districtId && <span role="alert" className="block mt-1 text-danger-strong font-medium">{errors.districtId}</span>}
          </label>
        </div>
      </form>

      <div className="bg-surface p-5 sm:p-8 rounded-3xl border border-line/90 shadow-xs">
        <LearningNeedManager
          defaults={{
            educationLevelId: profile?.educationLevelId,
            provinceId: profile?.provinceId ?? null,
            districtId: profile?.districtId ?? null,
          }}
        />
      </div>
    </div>
  );
}
