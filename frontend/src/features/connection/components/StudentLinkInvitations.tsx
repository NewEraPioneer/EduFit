"use client";

import { useCallback, useEffect, useState } from "react";
import { connectionApi } from "../api/connectionApi";
import type { LinkInvitation, StudentLink } from "../types";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";

import { HeartHandshake } from "lucide-react";
import { RevokeLinkModal } from "./RevokeLinkModal";

interface StudentLinkInvitationsProps {
  variant?: "full" | "dashboard-banner";
}

export function StudentLinkInvitations({ variant = "full" }: StudentLinkInvitationsProps) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [invitations, setInvitations] = useState<LinkInvitation[]>([]);
  const [links, setLinks] = useState<StudentLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [linkToRevoke, setLinkToRevoke] = useState<StudentLink | null>(null);

  const refresh = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      const [invitationResult, linksResult] = await Promise.allSettled([
        connectionApi.getLinkInvitations(),
        connectionApi.getLinks(),
      ]);
      if (invitationResult.status === "fulfilled") {
        setInvitations(invitationResult.value.filter((item) => item.status === "PENDING" && item.invitedUserId === user?.id));
        setError(null);
      } else {
        setError(invitationResult.reason instanceof Error ? invitationResult.reason.message : "Không thể tải lời mời liên kết.");
      }
      if (linksResult.status === "fulfilled") setLinks(linksResult.value);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải lời mời liên kết.");
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [user?.id]);

  useEffect(() => {
    void refresh(true);
    const refreshInBackground = () => { void refresh(); };
    const interval = window.setInterval(refreshInBackground, 60_000);
    window.addEventListener("focus", refreshInBackground);
    window.addEventListener("edufit:family-invitations-refresh", refreshInBackground);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener("focus", refreshInBackground);
      window.removeEventListener("edufit:family-invitations-refresh", refreshInBackground);
    };
  }, [refresh]);

  const respond = async (invitationId: string, accept: boolean) => {
    setBusyId(invitationId);
    try {
      await connectionApi.respondLinkInvitation(invitationId, accept);
      showToast(accept ? "Đã chấp nhận lời mời." : "Đã từ chối lời mời.");
      await refresh();
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : "Không thể xử lý lời mời.", "error");
    } finally {
      setBusyId(null);
    }
  };

  const handleConfirmRevoke = async (link: StudentLink, reason?: string) => {
    setBusyId(link.linkId);
    try {
      await connectionApi.revokeLink(link.linkId, reason);
      showToast("Đã hủy liên kết với phụ huynh.");
      setLinkToRevoke(null);
      await refresh();
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : "Không thể hủy liên kết.", "error");
      throw cause;
    } finally {
      setBusyId(null);
    }
  };

  if (variant === "dashboard-banner") {
    if (loading || error || invitations.length === 0) {
      return null;
    }

    return (
      <div className="space-y-3">
        {invitations.map((item) => (
          <div
            key={item.invitationId}
            className="rounded-2xl border border-amber-300 bg-linear-to-r from-amber-50 to-orange-50 p-4 sm:p-5 shadow-xs animate-in fade-in slide-in-from-top-2"
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-start gap-3.5">
                <div className="size-10 rounded-xl bg-amber-500 text-white flex items-center justify-center shrink-0 shadow-xs">
                  <HeartHandshake className="size-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-[11px] font-bold uppercase tracking-wide">
                      Lời mời liên kết mới
                    </span>
                  </div>
                  <h3 className="font-heading text-sm sm:text-base font-bold text-neutral-900 mt-1">
                    {item.inviterName || "Phụ huynh"} mời bạn liên kết
                  </h3>
                  <p className="text-xs text-neutral-600 mt-0.5">
                    Hết hạn: {new Date(item.expiresAt).toLocaleDateString("vi-VN")} · Khi đồng ý, phụ huynh có thể theo dõi tiến độ và lịch học của bạn.
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                <button
                  type="button"
                  disabled={busyId !== null}
                  onClick={() => void respond(item.invitationId, false)}
                  className="px-3.5 py-2 rounded-xl border border-stone-200 bg-white hover:bg-stone-50 text-neutral-700 text-xs font-bold transition-colors cursor-pointer disabled:opacity-50"
                >
                  Từ chối
                </button>
                <button
                  type="button"
                  disabled={busyId !== null}
                  onClick={() => void respond(item.invitationId, true)}
                  className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold shadow-xs transition-colors cursor-pointer disabled:opacity-50"
                >
                  Đồng ý
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <section id="family-invitations" aria-labelledby="family-invitations-title" className="rounded-2xl border border-stone-200 bg-white p-5 space-y-3 scroll-mt-20">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="family-invitations-title" className="font-heading text-base font-bold text-neutral-900">Lời mời liên kết gia đình</h2>
        <span className="text-xs text-neutral-500">{links.length} phụ huynh đã liên kết</span>
      </div>
      {loading ? (
        <p role="status" className="text-sm text-neutral-500">Đang tải lời mời...</p>
      ) : error ? (
        <p role="alert" className="text-sm text-rose-700">
          {error} <button type="button" onClick={() => void refresh()} className="font-bold underline">Thử lại</button>
        </p>
      ) : invitations.length === 0 ? (
        <p className="text-sm text-neutral-500">Không có lời mời đang chờ.</p>
      ) : (
        invitations.map((item) => (
          <div key={item.invitationId} className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-stone-200 p-4">
            <div>
              <p className="text-sm font-semibold text-neutral-900">{item.inviterName || "Phụ huynh"} mời bạn liên kết</p>
              <p className="text-xs text-neutral-500">Hết hạn: {new Date(item.expiresAt).toLocaleDateString("vi-VN")}</p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={busyId !== null}
                onClick={() => void respond(item.invitationId, false)}
                className="rounded-lg border border-stone-200 px-3 py-2 text-xs font-bold text-neutral-700 disabled:opacity-50"
              >
                Từ chối
              </button>
              <button
                type="button"
                disabled={busyId !== null}
                onClick={() => void respond(item.invitationId, true)}
                className="rounded-lg bg-amber-500 px-3 py-2 text-xs font-bold text-white disabled:opacity-50"
              >
                Đồng ý
              </button>
            </div>
          </div>
        ))
      )}

      <div className="border-t border-stone-100 pt-4 space-y-3">
        <h3 className="text-sm font-bold text-neutral-900">Phụ huynh đã liên kết</h3>
        {links.length === 0 ? (
          <p className="text-sm text-neutral-500">Chưa có phụ huynh nào được liên kết.</p>
        ) : (
          links.map((link) => (
            <div key={link.linkId} className="flex flex-col gap-3 rounded-xl border border-stone-200 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-semibold text-neutral-900">Tài khoản phụ huynh</p>
                <p className="text-xs text-neutral-500">Mã tài khoản: {link.parentUserId}</p>
                {link.confirmedAt && <p className="text-xs text-neutral-500">Liên kết ngày {new Date(link.confirmedAt).toLocaleDateString("vi-VN")}</p>}
              </div>
              <button
                type="button"
                disabled={busyId !== null}
                onClick={() => setLinkToRevoke(link)}
                className="rounded-lg border border-rose-200 px-3 py-2 text-xs font-bold text-rose-700 hover:bg-rose-50 transition-colors disabled:opacity-50 cursor-pointer"
              >
                Hủy liên kết
              </button>
            </div>
          ))
        )}
      </div>

      <RevokeLinkModal
        link={linkToRevoke}
        targetRole="parent"
        isOpen={linkToRevoke !== null}
        onClose={() => setLinkToRevoke(null)}
        onConfirm={handleConfirmRevoke}
      />
    </section>
  );
}
