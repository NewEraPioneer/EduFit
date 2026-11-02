"use client";

import React, { useEffect, useState, useMemo } from "react";
import {
  BookOpen,
  Plus,
  Search,
  CheckCircle2,
  XCircle,
  ShieldAlert,
  Loader2,
  RefreshCw,
  Info,
} from "lucide-react";
import { adminApi } from "../api/adminApi";
import { CreateSubjectModal } from "./CreateSubjectModal";
import type { SubjectAdmin } from "../types";

export function AdminCatalogView() {
  const [subjects, setSubjects] = useState<SubjectAdmin[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("ALL");
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [noticeMessage, setNoticeMessage] = useState<string | null>(null);

  const fetchSubjects = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminApi.getSubjects();
      setSubjects(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể tải danh mục môn học.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSubjects();
  }, []);

  const handleToggleStatus = async (subject: SubjectAdmin) => {
    setTogglingId(subject.id);
    try {
      const updated = await adminApi.toggleSubjectStatus(subject.id);
      setSubjects((prev) =>
        prev.map((s) => (s.id === subject.id ? { ...s, isActive: updated.isActive } : s))
      );
      setNoticeMessage(
        `Đã ${updated.isActive ? "kích hoạt" : "tạm ẩn"} môn học "${subject.name}" thành công.`
      );
      setTimeout(() => setNoticeMessage(null), 3500);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Chuyển đổi trạng thái môn học thất bại.");
    } finally {
      setTogglingId(null);
    }
  };

  const handleSubjectCreated = (newSubject: SubjectAdmin) => {
    setSubjects((prev) => [newSubject, ...prev]);
    setNoticeMessage(`Đã thêm môn học mới "${newSubject.name}" vào danh mục thành công.`);
    setTimeout(() => setNoticeMessage(null), 3500);
  };

  const categories = useMemo(() => {
    const set = new Set<string>();
    subjects.forEach((s) => {
      if (s.category) set.add(s.category);
    });
    return Array.from(set);
  }, [subjects]);

  const filteredSubjects = useMemo(() => {
    return subjects.filter((s) => {
      const matchesSearch =
        s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (s.code && s.code.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (s.slug && s.slug.toLowerCase().includes(searchQuery.toLowerCase()));

      const matchesCategory =
        categoryFilter === "ALL" || s.category === categoryFilter;

      return matchesSearch && matchesCategory;
    });
  }, [subjects, searchQuery, categoryFilter]);

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      {/* 1. Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-purple-100 dark:bg-purple-950/60 text-purple-700 dark:text-purple-400 flex items-center justify-center">
              <BookOpen className="size-4" />
            </div>
            <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
              Quản lý Danh mục Môn học
            </h2>
          </div>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400 mt-1 font-medium">
            Thiết lập danh mục các môn học và phân môn được phép gia sư đăng ký dạy và học sinh tìm kiếm.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={fetchSubjects}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white dark:bg-stone-900 hover:bg-stone-50 dark:hover:bg-stone-800 border border-stone-200 dark:border-stone-800 text-neutral-700 dark:text-stone-300 rounded-xl text-xs font-semibold shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer"
          >
            <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
            <span>Tải lại</span>
          </button>
          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 bg-purple-700 hover:bg-purple-800 text-white rounded-xl text-xs font-bold shadow-xs transition-colors shrink-0 cursor-pointer"
          >
            <Plus className="size-4" />
            <span>Thêm môn học mới</span>
          </button>
        </div>
      </div>

      {/* 2. Foreign Key Protection Notice */}
      <div className="p-4 rounded-2xl bg-amber-50/70 dark:bg-amber-950/20 border border-amber-200/80 dark:border-amber-800/40 text-amber-900 dark:text-amber-300 text-xs flex items-start gap-3">
        <Info className="size-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
        <div className="leading-relaxed">
          <span className="font-bold">Chính sách bảo toàn toàn vẹn dữ liệu EduFit:</span>{" "}
          Hệ thống nghiêm cấm xóa cứng (Hard Delete) môn học để bảo vệ toàn vẹn khóa ngoại (Foreign Key)
          với hồ sơ gia sư, yêu cầu học tập và lịch học đã diễn ra. Hãy sử dụng cơ chế Bật/Tắt trạng thái hoạt động (Soft-toggle) khi cần ẩn môn học khỏi giao diện tìm kiếm mới.
        </div>
      </div>

      {/* 3. Feedback Notification */}
      {noticeMessage && (
        <div className="p-3.5 rounded-2xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 text-xs font-semibold flex items-center gap-2 animate-in fade-in duration-150">
          <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400" />
          <span>{noticeMessage}</span>
        </div>
      )}

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 text-xs flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={fetchSubjects}
            className="underline font-bold ml-2 hover:text-red-900 dark:hover:text-red-300"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* 4. Filter & Search Controls */}
      <div className="bg-white dark:bg-stone-900 p-4 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400 dark:text-stone-500" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo tên môn, mã hoặc slug..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 dark:border-stone-700 text-xs text-neutral-900 dark:text-stone-100 bg-stone-50/50 dark:bg-stone-800/50 outline-none focus:border-purple-600 focus:bg-white dark:focus:bg-stone-900 transition-all"
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-neutral-500 dark:text-stone-400 font-medium whitespace-nowrap">
            Danh mục:
          </span>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="px-3 py-2 rounded-xl border border-stone-200 dark:border-stone-700 text-xs text-neutral-900 dark:text-stone-100 bg-white dark:bg-stone-800 outline-none focus:border-purple-600 transition-all"
          >
            <option value="ALL">Tất cả danh mục ({subjects.length})</option>
            {categories.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 5. Main Table Container */}
      <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs overflow-hidden transition-colors">
        {isLoading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-purple-700 dark:text-purple-400 animate-spin" />
            <span className="text-xs text-neutral-500 dark:text-stone-400 font-medium">
              Đang tải danh mục môn học...
            </span>
          </div>
        ) : filteredSubjects.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="size-12 rounded-2xl bg-stone-100 dark:bg-stone-800 text-neutral-400 dark:text-stone-500 flex items-center justify-center mx-auto">
              <BookOpen className="size-6" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-stone-100">
              Không tìm thấy môn học nào
            </h4>
            <p className="text-xs text-neutral-500 dark:text-stone-400 max-w-sm mx-auto">
              {searchQuery || categoryFilter !== "ALL"
                ? "Không có môn học nào khớp với bộ lọc tìm kiếm hiện tại. Hãy thử thay đổi từ khóa."
                : "Chưa có môn học nào trong danh mục. Hãy nhấn 'Thêm môn học mới' để khởi tạo."}
            </p>
            {(searchQuery || categoryFilter !== "ALL") && (
              <button
                type="button"
                onClick={() => {
                  setSearchQuery("");
                  setCategoryFilter("ALL");
                }}
                className="text-xs font-bold text-purple-700 dark:text-purple-400 hover:underline cursor-pointer"
              >
                Đặt lại bộ lọc
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-stone-200 dark:border-stone-800 bg-stone-50/70 dark:bg-stone-800/40 text-[11px] font-bold text-neutral-500 dark:text-stone-400 uppercase tracking-wider">
                  <th className="py-3.5 px-4 sm:px-6">Tên môn học</th>
                  <th className="py-3.5 px-4">Mã / Slug</th>
                  <th className="py-3.5 px-4">Danh mục</th>
                  <th className="py-3.5 px-4">Trạng thái</th>
                  <th className="py-3.5 px-4">Ngày tạo</th>
                  <th className="py-3.5 px-4 sm:px-6 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100 dark:divide-stone-800 text-xs">
                {filteredSubjects.map((subject) => {
                  const isToggling = togglingId === subject.id;
                  return (
                    <tr
                      key={subject.id}
                      className="hover:bg-stone-50/60 dark:hover:bg-stone-800/40 transition-colors"
                    >
                      <td className="py-3.5 px-4 sm:px-6">
                        <div className="font-bold text-neutral-900 dark:text-stone-100">
                          {subject.name}
                        </div>
                        {subject.description && (
                          <div className="text-[11px] text-neutral-500 dark:text-stone-400 line-clamp-1 mt-0.5">
                            {subject.description}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col gap-0.5">
                          {subject.code && (
                            <span className="font-mono text-[11px] font-semibold text-purple-700 dark:text-purple-400">
                              {subject.code}
                            </span>
                          )}
                          <span className="font-mono text-[10px] text-neutral-400 dark:text-stone-500">
                            /{subject.slug || subject.name.toLowerCase().replace(/\s+/g, "-")}
                          </span>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="px-2.5 py-1 rounded-lg bg-stone-100 dark:bg-stone-800 text-neutral-700 dark:text-stone-300 text-[11px] font-medium">
                          {subject.category || "Chung"}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        {subject.isActive ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60">
                            <CheckCircle2 className="size-3" />
                            <span>Hoạt động</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-stone-100 dark:bg-stone-800 text-neutral-500 dark:text-stone-400 border border-stone-200 dark:border-stone-700">
                            <XCircle className="size-3" />
                            <span>Tạm ẩn</span>
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-neutral-500 dark:text-stone-400 font-mono text-[11px]">
                        {subject.createdAt
                          ? new Date(subject.createdAt).toLocaleDateString("vi-VN")
                          : "Hệ thống"}
                      </td>
                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <button
                          type="button"
                          onClick={() => handleToggleStatus(subject)}
                          disabled={isToggling}
                          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer disabled:opacity-50 ${
                            subject.isActive
                              ? "bg-amber-50 dark:bg-amber-950/40 hover:bg-amber-100 dark:hover:bg-amber-900/50 text-amber-800 dark:text-amber-300 border border-amber-200/80 dark:border-amber-800"
                              : "bg-emerald-50 dark:bg-emerald-950/40 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 text-emerald-800 dark:text-emerald-300 border border-emerald-200/80 dark:border-emerald-800"
                          }`}
                        >
                          {isToggling ? (
                            <Loader2 className="size-3 animate-spin" />
                          ) : subject.isActive ? (
                            <span>Tạm ẩn</span>
                          ) : (
                            <span>Kích hoạt</span>
                          )}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 6. Modal Create */}
      <CreateSubjectModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={handleSubjectCreated}
      />
    </div>
  );
}
