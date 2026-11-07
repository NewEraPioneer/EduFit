"use client";

import React, { useEffect, useMemo, useState } from "react";
import { Loader2, Plus } from "lucide-react";
import { catalogService, type LearningCatalog } from "@/services/catalog";
import { progressApi } from "../api/progressApi";
import type { Topic } from "../types";
import { MAX_TOPIC_NAME_LENGTH, MIN_RADAR_AXES } from "../constants/roadmap";

interface EditState {
  topicId: string;
  name: string;
  sortOrder: number;
}

/**
 * Admin quản lý danh mục chủ đề chuẩn theo môn × khối (Giai đoạn 3). Chủ đề là trục của biểu đồ radar năng lực;
 * gia sư gắn chủ đề cho cột mốc khi lập lộ trình. Chủ đề đã dùng chỉ được ngừng sử dụng, không xóa.
 */
export function AdminTopicsView() {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [catalog, setCatalog] = useState<LearningCatalog | null>(null);
  const [subjectId, setSubjectId] = useState("");
  const [levelId, setLevelId] = useState("");
  const [newName, setNewName] = useState("");
  const [editing, setEditing] = useState<EditState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    Promise.all([progressApi.listTopics(), catalogService.getLearningCatalog()])
      .then(([res, cat]) => {
        if (ignore) return;
        setCatalog(cat);
        if (res.success && res.data) setTopics(res.data);
        else setError(res.message || "Không tải được danh mục chủ đề");
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof Error ? err.message : "Không tải được danh mục chủ đề");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, []);

  const visible = useMemo(
    () =>
      topics
        .filter((t) => t.subjectId === Number(subjectId) && t.educationLevelId === Number(levelId))
        .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, "vi")),
    [topics, subjectId, levelId]
  );
  const activeCount = visible.filter((t) => !t.archived).length;

  const upsert = (saved: Topic) =>
    setTopics((prev) =>
      prev.some((t) => t.topicId === saved.topicId)
        ? prev.map((t) => (t.topicId === saved.topicId ? saved : t))
        : [...prev, saved]
    );

  const run = async (action: () => ReturnType<typeof progressApi.createTopic>, fallback: string) => {
    setError(null);
    setSaving(true);
    try {
      const res = await action();
      if (res.success && res.data) {
        upsert(res.data);
        return true;
      }
      setError(res.message || fallback);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : fallback);
    } finally {
      setSaving(false);
    }
    return false;
  };

  const create = async () => {
    if (!newName.trim()) return;
    const nextOrder = visible.reduce((max, t) => Math.max(max, t.sortOrder), -1) + 1;
    const ok = await run(
      () =>
        progressApi.createTopic({
          subjectId: Number(subjectId),
          educationLevelId: Number(levelId),
          name: newName.trim(),
          sortOrder: nextOrder,
        }),
      "Không thêm được chủ đề"
    );
    if (ok) setNewName("");
  };

  const saveEdit = async (t: Topic) => {
    if (!editing) return;
    const ok = await run(
      () =>
        progressApi.updateTopic(t.topicId, {
          name: editing.name.trim(),
          sortOrder: editing.sortOrder,
          archived: t.archived,
          version: t.version,
        }),
      "Không lưu được chủ đề"
    );
    if (ok) setEditing(null);
  };

  const toggleArchived = (t: Topic) =>
    void run(
      () =>
        progressApi.updateTopic(t.topicId, {
          name: t.name,
          sortOrder: t.sortOrder,
          archived: !t.archived,
          version: t.version,
        }),
      "Không cập nhật được chủ đề"
    );

  const selected = subjectId !== "" && levelId !== "";

  return (
    <div className="p-6 md:p-8 max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-extrabold text-neutral-900">Danh mục chủ đề chuẩn</h1>
        <p className="text-xs text-neutral-500 mt-1">
          Chủ đề theo môn × khối là trục của biểu đồ radar năng lực. Radar chỉ hiện khi lộ trình có ít nhất{" "}
          {MIN_RADAR_AXES} chủ đề đã được chấm. Chủ đề đã dùng chỉ ngừng sử dụng, không xóa.
        </p>
      </div>

      {error && (
        <p role="alert" className="rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs text-rose-800">
          {error}
        </p>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <label className="text-xs font-bold text-neutral-700 space-y-1">
          <span>Môn học</span>
          <select
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
            className="w-full rounded-xl border border-stone-200 px-2 py-2 text-xs"
          >
            <option value="">— Chọn —</option>
            {catalog?.subjects.map((s) => (
              <option key={s.subjectId} value={s.subjectId}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        <label className="text-xs font-bold text-neutral-700 space-y-1">
          <span>Khối lớp</span>
          <select
            value={levelId}
            onChange={(e) => setLevelId(e.target.value)}
            className="w-full rounded-xl border border-stone-200 px-2 py-2 text-xs"
          >
            <option value="">— Chọn —</option>
            {catalog?.educationLevels.map((l) => (
              <option key={l.levelId} value={l.levelId}>
                {l.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading ? (
        <p className="flex items-center gap-2 text-xs text-neutral-500">
          <Loader2 className="size-3.5 animate-spin" /> Đang tải…
        </p>
      ) : !selected ? (
        <p className="rounded-2xl border border-dashed border-stone-200 p-6 text-center text-xs text-neutral-500">
          Chọn môn học và khối lớp để xem và thêm chủ đề.
        </p>
      ) : (
        <section aria-label="Chủ đề của môn và khối" className="space-y-3">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void create();
            }}
            className="flex gap-2"
          >
            <input
              aria-label="Tên chủ đề mới"
              value={newName}
              maxLength={MAX_TOPIC_NAME_LENGTH}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="Ví dụ: Hệ phương trình"
              className="flex-1 rounded-xl border border-stone-200 px-3 py-2 text-xs"
            />
            <button
              type="submit"
              disabled={saving || !newName.trim()}
              className="inline-flex items-center gap-1.5 min-h-10 px-4 rounded-xl bg-amber-500 text-white text-xs font-bold cursor-pointer disabled:opacity-50"
            >
              <Plus className="size-3.5" /> Thêm chủ đề
            </button>
          </form>
          <p className="text-xs text-neutral-500">{activeCount} chủ đề đang dùng</p>

          {visible.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-stone-200 p-6 text-center text-xs text-neutral-500">
              Môn và khối này chưa có chủ đề nào.
            </p>
          ) : (
            <ul className="space-y-2">
              {visible.map((t) => (
                <li
                  key={t.topicId}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-stone-200 bg-white p-3"
                >
                  {editing?.topicId === t.topicId ? (
                    <div className="flex flex-1 flex-wrap items-center gap-2">
                      <input
                        aria-label={`Tên chủ đề ${t.name}`}
                        value={editing.name}
                        maxLength={MAX_TOPIC_NAME_LENGTH}
                        onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                        className="flex-1 min-w-40 rounded-xl border border-stone-200 px-3 py-2 text-xs"
                      />
                      <label className="flex items-center gap-1.5 text-xs text-neutral-700">
                        Thứ tự
                        <input
                          type="number"
                          min={0}
                          value={editing.sortOrder}
                          onChange={(e) => setEditing({ ...editing, sortOrder: Number(e.target.value) })}
                          className="w-16 rounded-lg border border-stone-200 px-2 py-1"
                        />
                      </label>
                    </div>
                  ) : (
                    <div>
                      <p className={`text-sm font-bold ${t.archived ? "text-neutral-400 line-through" : "text-neutral-900"}`}>
                        {t.name}
                      </p>
                      <p className="text-xs text-neutral-500">
                        Thứ tự {t.sortOrder}
                        {t.archived && " · Đã ngừng sử dụng"}
                      </p>
                    </div>
                  )}
                  <div className="flex gap-2">
                    {editing?.topicId === t.topicId ? (
                      <>
                        <button
                          type="button"
                          onClick={() => setEditing(null)}
                          className="min-h-9 px-3 rounded-xl border border-stone-200 text-xs font-bold cursor-pointer"
                        >
                          Hủy
                        </button>
                        <button
                          type="button"
                          disabled={saving || !editing.name.trim()}
                          onClick={() => void saveEdit(t)}
                          className="min-h-9 px-3 rounded-xl bg-emerald-600 text-white text-xs font-bold cursor-pointer disabled:opacity-50"
                        >
                          Lưu
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          onClick={() => setEditing({ topicId: t.topicId, name: t.name, sortOrder: t.sortOrder })}
                          className="min-h-9 px-3 rounded-xl border border-stone-200 text-xs font-bold cursor-pointer"
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          disabled={saving}
                          aria-label={`${t.archived ? "Dùng lại" : "Ngừng dùng"} chủ đề ${t.name}`}
                          onClick={() => toggleArchived(t)}
                          className="min-h-9 px-3 rounded-xl border border-stone-200 text-xs font-bold cursor-pointer disabled:opacity-50"
                        >
                          {t.archived ? "Dùng lại" : "Ngừng dùng"}
                        </button>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}
