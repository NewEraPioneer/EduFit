"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { StudentLink } from "@/features/connection/types";
import { LearningNeedManager } from "@/features/learning-needs/components/LearningNeedManager";
import {
  Users,
  Save,
  Loader2,
  MapPin,
  Phone,
  User,
  Mail,
  ShieldCheck,
  CheckCircle2,
  ArrowRight,
  GraduationCap,
} from "lucide-react";

export function ParentProfileForm() {
  const { user, refreshUser, updateUser } = useAuth();
  const { showToast } = useToast();

  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [area, setArea] = useState("");
  const [linkedLinks, setLinkedLinks] = useState<StudentLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Nạp dữ liệu hồ sơ và liên kết thực tế khi mount
  useEffect(() => {
    let ignore = false;

    async function loadInitialData() {
      try {
        setLoading(true);

        // 1. Thông tin liên hệ phụ huynh lưu trên thiết bị này
        const savedProfileStr = localStorage.getItem("edufit_parent_profile");
        let saved: { fullName?: string; phoneNumber?: string; area?: string } = {};
        if (savedProfileStr) {
          try {
            saved = JSON.parse(savedProfileStr);
          } catch {
            saved = {};
          }
        }
        setFullName(saved.fullName || user?.fullName || "");
        setPhoneNumber(saved.phoneNumber || "");
        setArea(saved.area || "");

        // 2. Nạp danh sách liên kết học sinh thật từ Backend
        const links = await connectionApi.getLinks().catch(() => []);
        if (!ignore) {
          setLinkedLinks(links);
        }
      } catch (err: unknown) {
        console.error("Lỗi khi tải dữ liệu hồ sơ phụ huynh:", err);
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    void loadInitialData();

    return () => {
      ignore = true;
    };
  }, [user?.fullName]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!fullName.trim()) {
      showToast("Vui lòng nhập họ và tên phụ huynh!", "error");
      return;
    }

    if (!phoneNumber.trim()) {
      showToast("Vui lòng nhập số điện thoại liên hệ!", "error");
      return;
    }

    if (!area.trim()) {
      showToast("Vui lòng nhập khu vực sinh sống!", "error");
      return;
    }

    setSaving(true);

    try {
      // 1. Lưu cấu hình hồ sơ phụ huynh vào storage bền vững
      const profileData = {
        fullName: fullName.trim(),
        phoneNumber: phoneNumber.trim(),
        area: area.trim(),
        updatedAt: new Date().toISOString(),
      };
      localStorage.setItem("edufit_parent_profile", JSON.stringify(profileData));

      // 2. Cập nhật cache AuthContext và đồng bộ thông tin phiên làm việc
      if (user && updateUser) {
        updateUser({
          ...user,
          fullName: fullName.trim(),
        });
      }

      await refreshUser?.();
      showToast("Cập nhật thông tin phụ huynh thành công!", "success");
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu thông tin phụ huynh";
      showToast(message, "error");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="p-12 flex flex-col items-center justify-center gap-3 bg-white rounded-3xl border border-stone-200">
        <Loader2 className="size-6 text-emerald-600 animate-spin" />
        <span className="text-xs text-neutral-500 font-medium">
          Đang nạp dữ liệu hồ sơ phụ huynh...
        </span>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* 1. Header Card & Thông Tin Chung */}
      <div className="bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-stone-100">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-emerald-50 text-emerald-900 rounded-full text-xs font-bold mb-2">
              <Users className="size-3.5 text-emerald-600" />
              <span>Hồ Sơ Phụ Huynh (Parent Profile)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-neutral-900">
              Thông tin liên hệ & Nhu cầu học của con
            </h3>
            <p className="text-xs text-neutral-500 mt-0.5">
              Giúp gia sư nắm bắt nhu cầu học tập và liên hệ thuận tiện với gia đình
            </p>
          </div>

          <button
            type="submit"
            disabled={saving}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
          >
            {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            <span>{saving ? "Đang lưu..." : "Lưu thay đổi"}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {/* Họ tên phụ huynh */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Họ và tên phụ huynh <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <User className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="VD: Trần Văn Hưng"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 text-neutral-900 font-medium"
              />
            </div>
          </div>

          {/* Email tài khoản (read-only) */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Email đăng nhập hệ thống
            </label>
            <div className="relative">
              <Mail className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="email"
                disabled
                value={user?.email ?? ""}
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-100 border border-stone-200 rounded-xl text-neutral-500 font-medium cursor-not-allowed"
              />
            </div>
          </div>

          {/* Số điện thoại liên hệ */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Số điện thoại liên hệ <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <Phone className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="tel"
                required
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="VD: 0988 123 456"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 text-neutral-900 font-medium"
              />
            </div>
          </div>

          {/* Khu vực sinh sống */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Khu vực sinh sống / Địa chỉ học tập <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <MapPin className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                required
                value={area}
                onChange={(e) => setArea(e.target.value)}
                placeholder="VD: Cầu Giấy, Hà Nội"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 text-neutral-900 font-medium"
              />
            </div>
          </div>
        </div>
      </div>

      {/* 2. Nhu cầu học của từng con: lưu trên hệ thống, dùng để ghép và gửi cho gia sư */}
      {linkedLinks.map((link, index) => (
        <div key={link.linkId} className="bg-white p-5 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs space-y-2">
          <p className="text-[11px] font-semibold text-emerald-700 break-all">
            Học sinh {index + 1} · mã {link.studentId.slice(-8)}
          </p>
          <LearningNeedManager studentId={link.studentId} />
        </div>
      ))}

      {/* 3. Liên Kết Tài Khoản Con Em (Parent - Student Link) Thật */}
      <div className="bg-stone-50/80 p-6 rounded-3xl border border-stone-200 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldCheck className="size-4 text-emerald-600" />
            <h4 className="text-xs font-bold text-neutral-800">
              Trạng thái liên kết hồ sơ con em (Parent-Student Link)
            </h4>
          </div>
          {linkedLinks.length > 0 ? (
            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 bg-emerald-100/80 text-emerald-800 rounded-full text-[10px] font-bold">
              <CheckCircle2 className="size-3 text-emerald-600" />
              Đã kết nối {linkedLinks.length} học sinh
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 bg-amber-100 text-amber-800 rounded-full text-[10px] font-bold">
              Chưa có liên kết
            </span>
          )}
        </div>

        {linkedLinks.length > 0 ? (
          <div className="space-y-2">
            {linkedLinks.map((link) => (
              <div
                key={link.linkId}
                className="p-3 bg-white rounded-2xl border border-stone-200 flex items-center justify-between"
              >
                <div className="flex items-center gap-2.5">
                  <div className="size-8 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
                    <GraduationCap className="size-4" />
                  </div>
                  <div>
                    <p className="text-xs font-bold text-neutral-900">
                      Mã hồ sơ học sinh: {link.studentId}
                    </p>
                    <p className="text-[10px] text-neutral-400">
                      {link.confirmedAt
                        ? `Xác nhận lúc: ${new Date(link.confirmedAt).toLocaleDateString("vi-VN")}`
                        : "Trạng thái: CONFIRMED"}
                    </p>
                  </div>
                </div>
                <Link
                  href="/parent/children"
                  className="text-xs text-emerald-700 font-semibold hover:underline"
                >
                  Chi tiết →
                </Link>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 bg-white rounded-2xl border border-stone-200">
            <p className="text-[11px] text-neutral-500 leading-relaxed">
              Tài khoản của bạn hiện chưa liên kết với hồ sơ học sinh nào. Hãy gửi lời mời liên kết tới email tài khoản của con để bắt đầu theo dõi tiến độ và lịch học.
            </p>
            <Link
              href="/parent/children"
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow-xs shrink-0 transition-colors"
            >
              <span>Quản lý liên kết</span>
              <ArrowRight className="size-3.5" />
            </Link>
          </div>
        )}
      </div>
    </form>
  );
}
