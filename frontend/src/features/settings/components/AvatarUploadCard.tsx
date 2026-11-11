"use client";

import React, { useRef, useState } from "react";
import Image from "next/image";
import { Camera, Trash2, Upload, Sparkles, Loader2, User as UserIcon } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { authApi } from "@/features/auth/api/authApi";
import { useToast } from "@/shared/ui/Toast";

// Danh sách các avatar mẫu chất lượng cao (DiceBear Avatars) để người dùng chọn nhanh
const PRESET_AVATARS = [
  {
    id: "preset-1",
    name: "Tri thức",
    url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix&backgroundColor=ffdfbf",
  },
  {
    id: "preset-2",
    name: "Năng động",
    url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka&backgroundColor=c0aede",
  },
  {
    id: "preset-3",
    name: "Gia sư vui vẻ",
    url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Leo&backgroundColor=b6e3f4",
  },
  {
    id: "preset-4",
    name: "Học giả trẻ",
    url: "https://api.dicebear.com/7.x/avataaars/svg?seed=Milo&backgroundColor=d1d4f9",
  },
  {
    id: "preset-5",
    name: "Sáng tạo",
    url: "https://api.dicebear.com/7.x/bottts/svg?seed=EduBot&backgroundColor=ffd5dc",
  },
  {
    id: "preset-6",
    name: "Thân thiện",
    url: "https://api.dicebear.com/7.x/fun-emoji/svg?seed=Sunny&backgroundColor=ffdfbf",
  },
];

export function AvatarUploadCard() {
  const { user, updateUser, refreshUser } = useAuth();
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [uploading, setUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Xử lý chọn tệp từ máy
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setErrorMsg(null);

    // Kiểm tra định dạng
    const validTypes = ["image/jpeg", "image/png", "image/webp", "image/gif"];
    if (!validTypes.includes(file.type)) {
      setErrorMsg("Định dạng tệp không hợp lệ! Vui lòng chọn ảnh JPG, PNG, WEBP hoặc GIF.");
      showToast("Định dạng tệp không hợp lệ!", "error");
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }

    // Kiểm tra dung lượng (tối đa 5MB)
    const maxSize = 5 * 1024 * 1024;
    if (file.size > maxSize) {
      setErrorMsg("Kích thước tệp vượt quá 5MB. Vui lòng chọn tệp nhỏ hơn.");
      showToast("Kích thước tệp vượt quá 5MB!", "error");
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }

    setUploading(true);
    try {
      const res = await authApi.uploadAvatar(file);
      if (res.success && res.data) {
        if (updateUser) updateUser(res.data);
        await refreshUser();
        showToast("Tải lên và cập nhật ảnh đại diện thành công!", "success");
      } else {
        throw new Error(res.message || "Tải lên ảnh thất bại");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Tải lên ảnh đại diện thất bại. Vui lòng thử lại.";
      setErrorMsg(msg);
      showToast(msg, "error");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  // Chọn ảnh mẫu có sẵn (Preset)
  const handleSelectPreset = async (presetUrl: string) => {
    setErrorMsg(null);
    setUploading(true);
    try {
      const res = await authApi.updateAvatarUrl(presetUrl);
      if (res.success && res.data) {
        if (updateUser) updateUser(res.data);
        await refreshUser();
        showToast("Đã cập nhật ảnh đại diện mẫu thành công!", "success");
      } else {
        throw new Error(res.message || "Cập nhật ảnh đại diện thất bại");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Cập nhật ảnh đại diện thất bại";
      setErrorMsg(msg);
      showToast(msg, "error");
    } finally {
      setUploading(false);
    }
  };

  // Xóa ảnh đại diện đưa về mặc định
  const handleRemoveAvatar = async () => {
    if (!user?.avatarUrl) return;

    setErrorMsg(null);
    setUploading(true);
    try {
      const res = await authApi.removeAvatar();
      if (res.success && res.data) {
        if (updateUser) updateUser(res.data);
        await refreshUser();
        showToast("Đã xóa ảnh đại diện thành công!", "success");
      } else {
        throw new Error(res.message || "Xóa ảnh đại diện thất bại");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Không thể xóa ảnh đại diện";
      setErrorMsg(msg);
      showToast(msg, "error");
    } finally {
      setUploading(false);
    }
  };

  // Tạo URL hiển thị ảnh: nếu là link local /api/... thì trỏ về backend hoặc relative
  const currentAvatarSrc = user?.avatarUrl;

  return (
    <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/90 shadow-xs space-y-6">
      <div className="flex items-center justify-between pb-3 border-b border-line">
        <div className="flex items-center gap-2.5 text-xs font-medium text-ink">
          <Camera className="size-4 text-action-strong" />
          <span>Ảnh đại diện hồ sơ (Avatar)</span>
        </div>
        {user?.avatarUrl && (
          <span className="text-xs font-medium text-success-strong bg-success-soft px-2 py-0.5 rounded-md">
            Đã tùy chỉnh
          </span>
        )}
      </div>

      {errorMsg && (
        <div className="p-3.5 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-2xl animate-in fade-in">
          {errorMsg}
        </div>
      )}

      {/* Vùng hiển thị Avatar và Nút tải lên */}
      <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6">
        {/* Avatar Preview */}
        <div className="relative group shrink-0">
          <div className="size-24 sm:size-28 rounded-full overflow-hidden border-2 border-line bg-sunken shadow-inner flex items-center justify-center">
            {currentAvatarSrc ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={currentAvatarSrc}
                alt={user?.fullName || "Ảnh đại diện"}
                className="size-full object-cover"
              />
            ) : (
              <div className="flex flex-col items-center justify-center text-muted">
                <UserIcon className="size-12" />
                <span className="text-xs font-medium mt-0.5">Mặc định</span>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="absolute bottom-0 right-0 size-8 rounded-full bg-action hover:bg-action-deep text-on-action shadow-md flex items-center justify-center cursor-pointer transition-transform active:scale-95 disabled:opacity-50"
            title="Đổi ảnh đại diện"
          >
            {uploading ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Camera className="size-4" />
            )}
          </button>
        </div>

        {/* Action Buttons & Note */}
        <div className="flex-1 space-y-3 text-center sm:text-left">
          <div>
            <h4 className="text-sm font-medium text-ink">
              {user?.fullName || "Người dùng"}
            </h4>
            <p className="text-xs text-muted mt-0.5">
              Ảnh đại diện sẽ hiển thị trên thanh điều hướng, kết quả tìm kiếm gia sư và lịch học của bạn.
            </p>
          </div>

          {/* Hidden File Input */}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            className="hidden"
            onChange={handleFileChange}
          />

          <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2.5 pt-1">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="inline-flex items-center gap-2 px-4 py-2 bg-night hover:bg-night text-white text-xs font-medium rounded-xl shadow-xs transition-colors cursor-pointer disabled:opacity-50"
            >
              {uploading ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Upload className="size-3.5" />
              )}
              <span>Tải ảnh từ thiết bị</span>
            </button>

            {user?.avatarUrl && (
              <button
                type="button"
                onClick={handleRemoveAvatar}
                disabled={uploading}
                className="inline-flex items-center gap-1.5 px-3 py-2 bg-sunken hover:bg-danger-soft text-ink-2 hover:text-danger-strong text-xs font-medium rounded-xl transition-colors cursor-pointer disabled:opacity-50"
              >
                <Trash2 className="size-3.5" />
                <span>Xóa ảnh</span>
              </button>
            )}
          </div>

          <p className="text-xs text-muted">
            Hỗ trợ tệp PNG, JPG, WEBP hoặc GIF (Dung lượng tối đa 5MB).
          </p>
        </div>
      </div>

      {/* 2. Kho ảnh mẫu tiện lợi (Preset Avatars) */}
      <div className="pt-4 border-t border-line space-y-3">
        <div className="flex items-center gap-1.5 text-xs font-medium text-ink-2">
          <Sparkles className="size-3.5 text-action-strong" />
          <span>Hoặc chọn nhanh từ bộ sưu tập ảnh mẫu:</span>
        </div>

        <div className="grid grid-cols-3 sm:grid-cols-6 gap-3">
          {PRESET_AVATARS.map((preset) => {
            const isSelected = user?.avatarUrl === preset.url;
            return (
              <button
                key={preset.id}
                type="button"
                onClick={() => handleSelectPreset(preset.url)}
                disabled={uploading}
                className={`group flex flex-col items-center gap-1.5 p-2 rounded-2xl border transition-all cursor-pointer ${
                  isSelected
                    ? "border-action bg-action-soft/50 shadow-2xs ring-2 ring-action/20"
                    : "border-line hover:border-action-line hover:bg-paper"
                } disabled:opacity-50`}
              >
                <div className="size-12 rounded-full overflow-hidden border border-line transition-transform bg-surface">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={preset.url}
                    alt={preset.name}
                    className="size-full object-cover"
                  />
                </div>
                <span className="text-xs font-medium text-ink-2 truncate max-w-full">
                  {preset.name}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
