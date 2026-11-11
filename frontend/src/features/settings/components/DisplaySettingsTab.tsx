"use client";

import React, { useState } from "react";
import { Globe, Clock, Coins, Check } from "lucide-react";
import { useToast } from "@/shared/ui/Toast";

export function DisplaySettingsTab() {
  const { showToast } = useToast();

  const [language, setLanguage] = useState("vi");
  const [dateFormat, setDateFormat] = useState("DD/MM/YYYY");
  const [currency, setCurrency] = useState("VND");
  const [saving, setSaving] = useState(false);

  const handleSave = () => {
    setSaving(true);
    setTimeout(() => {
      setSaving(false);
      showToast("Đã lưu tùy chọn hiển thị thành công!", "success");
    }, 400);
  };

  return (
    <div className="space-y-6">
      <div className="pb-4 border-b border-line">
        <h2 className="font-heading text-xl font-bold text-ink">
          Tùy chọn hiển thị & Khu vực
        </h2>
        <p className="text-xs text-muted mt-1">
          Cài đặt ngôn ngữ, định dạng ngày tháng và tiền tệ sử dụng trong nền tảng EduFit.
        </p>
      </div>

      <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/90 shadow-xs space-y-5 max-w-xl">
        {/* 1. Ngôn ngữ */}
        <div>
          <label className="flex items-center gap-2 text-xs font-medium text-ink-2 mb-2">
            <Globe className="size-4 text-action-strong" />
            <span>Ngôn ngữ giao diện</span>
          </label>
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
            className="w-full px-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 text-ink"
          >
            <option value="vi">Tiếng Việt (Mặc định)</option>
            <option value="en">English (Coming soon)</option>
          </select>
        </div>

        {/* 2. Tiền tệ */}
        <div>
          <label className="flex items-center gap-2 text-xs font-medium text-ink-2 mb-2">
            <Coins className="size-4 text-success-strong" />
            <span>Đơn vị tiền tệ hiển thị</span>
          </label>
          <select
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            className="w-full px-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 text-ink"
          >
            <option value="VND">VNĐ (Việt Nam Đồng)</option>
            <option value="USD">USD ($)</option>
          </select>
        </div>

        {/* 3. Định dạng ngày */}
        <div>
          <label className="flex items-center gap-2 text-xs font-medium text-ink-2 mb-2">
            <Clock className="size-4 text-brand-strong" />
            <span>Định dạng ngày & giờ</span>
          </label>
          <select
            value={dateFormat}
            onChange={(e) => setDateFormat(e.target.value)}
            className="w-full px-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 text-ink"
          >
            <option value="DD/MM/YYYY">DD/MM/YYYY (Ví dụ: 12/10/2026)</option>
            <option value="YYYY-MM-DD">YYYY-MM-DD (Ví dụ: 2026-10-12)</option>
          </select>
          <p className="text-xs text-muted mt-1 pl-1">
            Múi giờ chuẩn: Asia/Ho_Chi_Minh (GMT+7)
          </p>
        </div>

        <div className="pt-2">
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="inline-flex items-center gap-2 px-6 py-2.5 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-xl shadow-xs transition-all cursor-pointer"
          >
            <Check className="size-3.5" />
            <span>{saving ? "Đang lưu..." : "Lưu tùy chọn"}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
