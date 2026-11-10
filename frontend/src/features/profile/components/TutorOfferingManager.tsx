"use client";

import React, { useState } from "react";
import { createPortal } from "react-dom";
import { BookOpen, Loader2, Pencil, Plus, Trash2, X } from "lucide-react";
import { profileService, type TutorOffering, type TutorProfileData } from "@/services/profile";
import { useLearningCatalog } from "@/features/learning-needs/hooks/useLearningCatalog";
import { OfferingList } from "@/features/discovery/components/OfferingList";
import { formatVnd, offeringName } from "@/features/discovery/utils/tutorDisplay";
import { TutorOfferingForm } from "./TutorOfferingForm";

export interface TutorOfferingManagerProps {
  offerings: TutorOffering[];
  /** Hồ sơ mới nhất sau mỗi lần thêm/sửa/xóa. */
  onChange: (profile: TutorProfileData) => void;
}

/** Quản lý gói dạy của gia sư; mỗi gói có học phí riêng và được ghép theo môn của nhu cầu học. */
export function TutorOfferingManager({ offerings, onChange }: TutorOfferingManagerProps) {
  const { catalog, error: catalogError } = useLearningCatalog();
  const [editing, setEditing] = useState<TutorOffering | null>(null);
  const [creating, setCreating] = useState(false);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const remove = async (offering: TutorOffering) => {
    setConfirmingId(null);
    setDeletingId(offering.offeringId);
    setError(null);
    try {
      onChange(await profileService.deleteTutorOffering(offering.offeringId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể xóa gói dạy.");
    } finally {
      setDeletingId(null);
    }
  };

  const dialogOpen = creating || editing !== null;

  return (
    <section className="space-y-4" aria-labelledby="tutor-offerings-heading">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <BookOpen className="size-4 text-brand-strong" />
          <h4 id="tutor-offerings-heading" className="text-xs font-medium text-ink">Gói dạy</h4>
        </div>
        <button type="button" onClick={() => setCreating(true)}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-brand hover:bg-brand-deep text-white text-xs font-medium">
          <Plus className="size-3.5" />Thêm gói dạy
        </button>
      </div>
      <p className="text-xs text-muted">
        Mỗi gói gồm một môn, các khối lớp, mục tiêu nhận dạy và học phí riêng. Học sinh được ghép theo môn trong nhu cầu học.
      </p>

      {(error || catalogError) && <p role="alert" className="text-xs text-danger-strong">{error ?? catalogError}</p>}

      {offerings.length === 0 ? (
        <div className="py-4 text-center text-xs text-muted border border-dashed border-line rounded-2xl">
          Chưa có gói dạy nào. Thêm gói dạy để xuất hiện trong kết quả tìm kiếm.
        </div>
      ) : (
        <ul className="space-y-2">
          {offerings.map((offering) => (
            <li key={offering.offeringId} className="flex flex-col sm:flex-row sm:items-start gap-2">
              <div className="flex-1 min-w-0"><OfferingList offerings={[offering]} /></div>
              <div className="flex gap-2 shrink-0">
                <button type="button" onClick={() => setEditing(offering)} aria-label={`Sửa gói ${offeringName(offering)}`}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-line text-xs font-medium text-ink-2 hover:bg-paper">
                  <Pencil className="size-3.5" />Sửa
                </button>
                {confirmingId === offering.offeringId ? (
                  <>
                    <button type="button" onClick={() => remove(offering)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-danger text-white text-xs font-medium hover:bg-danger-deep">
                      Xác nhận xóa
                    </button>
                    <button type="button" onClick={() => setConfirmingId(null)}
                      className="px-3 py-1.5 rounded-lg border border-line text-xs font-medium text-ink-2 hover:bg-paper">
                      Hủy
                    </button>
                  </>
                ) : (
                  <button type="button" onClick={() => setConfirmingId(offering.offeringId)} disabled={deletingId === offering.offeringId}
                    aria-label={`Xóa gói ${offeringName(offering)} (${formatVnd(offering.pricePerSession)})`}
                    className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-line text-xs font-medium text-danger-strong hover:bg-danger-soft disabled:opacity-50">
                    {deletingId === offering.offeringId ? <Loader2 className="size-3.5 animate-spin" /> : <Trash2 className="size-3.5" />}Xóa
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* Portal: trình quản lý có thể nằm trong form hồ sơ, form gói dạy không được lồng vào đó */}
      {dialogOpen && createPortal(
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/50 backdrop-blur-xs overflow-y-auto">
          <div role="dialog" aria-modal="true" aria-labelledby="offering-dialog-title"
            className="bg-surface rounded-3xl max-w-dialog w-full border border-line shadow-2xl my-auto">
            <div className="px-6 py-5 border-b border-line flex items-start justify-between gap-4">
              <h3 id="offering-dialog-title" className="font-heading font-bold text-base text-ink">
                {editing ? `Sửa gói dạy ${offeringName(editing)}` : "Thêm gói dạy"}
              </h3>
              <button type="button" aria-label="Đóng" onClick={() => { setCreating(false); setEditing(null); }}
                className="p-2 text-muted hover:text-ink-2 hover:bg-sunken rounded-full">
                <X className="size-4" />
              </button>
            </div>
            <div className="p-6">
              {!catalog ? (
                <p className="text-sm text-muted inline-flex items-center gap-2"><Loader2 className="size-4 animate-spin" />Đang tải danh mục…</p>
              ) : (
                <TutorOfferingForm
                  catalog={catalog}
                  offering={editing}
                  onCancel={() => { setCreating(false); setEditing(null); }}
                  onSaved={(profile) => {
                    setCreating(false);
                    setEditing(null);
                    onChange(profile);
                  }}
                />
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </section>
  );
}
