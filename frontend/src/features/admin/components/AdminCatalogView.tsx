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
            <div className="size-8 rounded-xl bg-brand-soft text-brand-strong flex items-center justify-center">
              <BookOpen className="size-4" />
            </div>
            <h2 className="font-heading text-2xl sm:text-3xl font-bold text-ink tracking-tight">
              Quản lý Danh mục Môn học
            </h2>
          </div>
          <p className="text-xs sm:text-sm text-muted mt-1 font-medium">
            Thiết lập danh mục các môn học và phân môn được phép gia sư đăng ký dạy và học sinh tìm kiếm.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={fetchSubjects}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-surface hover:bg-paper border border-line text-ink-2 rounded-xl text-xs font-medium shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer"
          >
            <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
            <span>Tải lại</span>
          </button>
          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium shadow-xs transition-colors shrink-0 cursor-pointer"
          >
            <Plus className="size-4" />
            <span>Thêm môn học mới</span>
          </button>
        </div>
      </div>

      {/* 2. Foreign Key Protection Notice */}
      <div className="p-4 rounded-2xl bg-action-soft/70 border border-action-line/80 text-action-strong text-xs flex items-start gap-3">
        <Info className="size-4 shrink-0 text-action-strong mt-0.5" />
        <div className="leading-relaxed">
          <span className="font-medium">Chính sách bảo toàn toàn vẹn dữ liệu EduFit:</span>{" "}
          Hệ thống nghiêm cấm xóa cứng (Hard Delete) môn học để bảo vệ toàn vẹn khóa ngoại (Foreign Key)
          với hồ sơ gia sư, yêu cầu học tập và lịch học đã diễn ra. Để ẩn môn học, hãy tắt trạng thái hoạt động của môn đó khỏi kết quả tìm kiếm mới.
        </div>
      </div>

      {/* 3. Feedback Notification */}
      {noticeMessage && (
        <div className="p-3.5 rounded-2xl bg-success-soft border border-success-line text-success-strong text-xs font-medium flex items-center gap-2 animate-in fade-in duration-150">
          <CheckCircle2 className="size-4 text-success-strong" />
          <span>{noticeMessage}</span>
        </div>
      )}

      {error && (
        <div className="p-4 rounded-2xl bg-danger-soft border border-danger-line text-danger-strong text-xs flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={fetchSubjects}
            className="underline font-medium ml-2 hover:text-danger-strong"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* 4. Filter & Search Controls */}
      <div className="bg-surface p-4 rounded-2xl border border-line/80 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo tên môn, mã hoặc slug..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border border-line text-xs text-ink bg-paper/50 outline-none focus:border-brand focus:bg-surface transition-all"
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-muted font-medium whitespace-nowrap">
            Danh mục:
          </span>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="px-3 py-2 rounded-xl border border-line text-xs text-ink bg-surface outline-none focus:border-brand transition-all"
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
      <div className="bg-surface rounded-3xl border border-line/80 shadow-xs overflow-hidden transition-colors">
        {isLoading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-brand-strong animate-spin" />
            <span className="text-xs text-muted font-medium">
              Đang tải danh mục môn học...
            </span>
          </div>
        ) : filteredSubjects.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="size-12 rounded-2xl bg-sunken text-muted flex items-center justify-center mx-auto">
              <BookOpen className="size-6" />
            </div>
            <h4 className="text-sm font-medium text-ink">
              Không tìm thấy môn học nào
            </h4>
            <p className="text-xs text-muted max-w-sm mx-auto">
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
                className="text-xs font-medium text-brand-strong hover:underline cursor-pointer"
              >
                Đặt lại bộ lọc
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-line bg-paper/70 text-xs font-medium text-muted">
                  <th className="py-3.5 px-4 sm:px-6">Tên môn học</th>
                  <th className="py-3.5 px-4">Mã / Slug</th>
                  <th className="py-3.5 px-4">Danh mục</th>
                  <th className="py-3.5 px-4">Trạng thái</th>
                  <th className="py-3.5 px-4">Ngày tạo</th>
                  <th className="py-3.5 px-4 sm:px-6 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line text-xs">
                {filteredSubjects.map((subject) => {
                  const isToggling = togglingId === subject.id;
                  return (
                    <tr
                      key={subject.id}
                      className="hover:bg-paper/60 transition-colors"
                    >
                      <td className="py-3.5 px-4 sm:px-6">
                        <div className="font-medium text-ink">
                          {subject.name}
                        </div>
                        {subject.description && (
                          <div className="text-xs text-muted line-clamp-1 mt-0.5">
                            {subject.description}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col gap-0.5">
                          {subject.code && (
                            <span className="font-mono text-xs font-medium text-brand-strong">
                              {subject.code}
                            </span>
                          )}
                          <span className="font-mono text-xs text-muted">
                            /{subject.slug || subject.name.toLowerCase().replace(/\s+/g, "-")}
                          </span>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="px-2.5 py-1 rounded-lg bg-sunken text-ink-2 text-xs font-medium">
                          {subject.category || "Chung"}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        {subject.isActive ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-success-soft text-success-strong border border-success-line">
                            <CheckCircle2 className="size-3" />
                            <span>Hoạt động</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-sunken text-muted border border-line">
                            <XCircle className="size-3" />
                            <span>Tạm ẩn</span>
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-muted font-mono text-xs">
                        {subject.createdAt
                          ? new Date(subject.createdAt).toLocaleDateString("vi-VN")
                          : "Hệ thống"}
                      </td>
                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <button
                          type="button"
                          onClick={() => handleToggleStatus(subject)}
                          disabled={isToggling}
                          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium transition-all cursor-pointer disabled:opacity-50 ${
                            subject.isActive
                              ? "bg-action-soft hover:bg-action-soft text-action-strong border border-action-line/80"
                              : "bg-success-soft hover:bg-success-soft text-success-strong border border-success-line/80"
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
