"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { ArrowLeft, UserPlus, Users } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { LinkInvitation, StudentLink } from "@/features/connection/types";
import { RevokeLinkModal } from "@/features/connection/components/RevokeLinkModal";
import { progressApi } from "@/features/progress";
import { useToast } from "@/shared/ui/Toast";

export default function ParentChildrenPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [links, setLinks] = useState<StudentLink[]>([]);
  const [invitations, setInvitations] = useState<LinkInvitation[]>([]);
  const [names, setNames] = useState<Record<string, string>>({});
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [linkToRevoke, setLinkToRevoke] = useState<StudentLink | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [confirmed, allInvitations] = await Promise.all([
        connectionApi.getLinks(),
        connectionApi.getLinkInvitations(),
      ]);
      setLinks(confirmed);
      setInvitations(allInvitations.filter((item) => item.status === "PENDING" && item.inviterUserId === user?.id));
      const progress = await progressApi.getParentDashboard().catch(() => null);
      setNames(Object.fromEntries((progress?.data?.students ?? [])
        .filter((student) => student.studentId && student.studentName)
        .map((student) => [student.studentId, student.studentName])));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải liên kết gia đình.");
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  useEffect(() => { void loadData(); }, [loadData]);

  const invite = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const targetEmail = email.trim();
    if (!targetEmail) return;
    setBusyId("invite");
    try {
      await connectionApi.inviteParentStudent(targetEmail);
      setEmail("");
      showToast("Đã gửi lời mời liên kết.");
      await loadData();
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : "Không thể gửi lời mời.", "error");
    } finally {
      setBusyId(null);
    }
  };

  const withdraw = async (invitationId: string) => {
    setBusyId(invitationId);
    try {
      await connectionApi.withdrawLinkInvitation(invitationId);
      showToast("Đã rút lời mời.");
      await loadData();
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : "Không thể rút lời mời.", "error");
    } finally {
      setBusyId(null);
    }
  };

  const handleConfirmRevoke = async (link: StudentLink, reason?: string) => {
    setBusyId(link.linkId);
    try {
      await connectionApi.revokeLink(link.linkId, reason);
      showToast("Đã hủy liên kết.");
      setLinkToRevoke(null);
      await loadData();
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : "Không thể hủy liên kết.", "error");
      throw cause;
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main className="max-w-5xl mx-auto space-y-6 sm:space-y-8">
      <header className="border-b border-stone-200 pb-5">
        <Link href="/parent/dashboard" className="inline-flex items-center gap-1.5 text-xs font-semibold text-neutral-500 hover:text-neutral-900 mb-2">
          <ArrowLeft className="size-3.5" /> Quay lại Tổng quan
        </Link>
        <h1 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 flex items-center gap-2.5">
          <Users className="size-7 text-emerald-600" /> Quản lý liên kết con em
        </h1>
        <p className="text-sm text-neutral-500 mt-1">Theo dõi các con đã liên kết và gửi lời mời tới tài khoản học sinh.</p>
      </header>

      {error && <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
        {error} <button type="button" onClick={() => void loadData()} className="font-bold underline">Thử lại</button>
      </div>}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <section className="lg:col-span-7 space-y-4" aria-labelledby="linked-children-title">
          <h2 id="linked-children-title" className="text-sm font-bold text-neutral-900 uppercase tracking-wider">Con đã liên kết ({links.length})</h2>
          {loading ? <p role="status" className="rounded-2xl border border-stone-200 bg-white p-8 text-sm text-neutral-500">Đang tải liên kết...</p>
            : !error && links.length === 0 ? <p className="rounded-2xl border border-dashed border-stone-300 bg-white p-8 text-sm text-neutral-600">Chưa có học sinh nào được liên kết.</p>
              : links.map((link) => <article key={link.linkId} className="rounded-2xl border border-stone-200 bg-white p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h3 className="text-sm font-bold text-neutral-900">{names[link.studentId] || `Học sinh ${link.studentId.slice(0, 8)}`}</h3>
                  <p className="text-xs text-neutral-500 mt-1">Mã học sinh: {link.studentId}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link href="/parent/schedule" className="rounded-lg border border-stone-200 px-3 py-2 text-xs font-semibold">Lịch học</Link>
                  <Link href="/parent/progress" className="rounded-lg border border-stone-200 px-3 py-2 text-xs font-semibold">Tiến độ</Link>
                  <button type="button" disabled={busyId !== null} onClick={() => setLinkToRevoke(link)} className="rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-50 transition-colors disabled:opacity-50 cursor-pointer">Hủy liên kết</button>
                </div>
              </article>)}

          <h2 className="text-sm font-bold text-neutral-900 uppercase tracking-wider pt-3">Lời mời đang chờ ({invitations.length})</h2>
          {!loading && invitations.length === 0 && <p className="text-sm text-neutral-500">Không có lời mời đang chờ.</p>}
          {invitations.map((item) => <div key={item.invitationId} className="rounded-2xl border border-stone-200 bg-white p-4 flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-neutral-800">Đang chờ học sinh xác nhận</p>
              <p className="text-xs text-neutral-500">Mã lời mời: {item.invitationId.slice(0, 8)} · Hết hạn: {new Date(item.expiresAt).toLocaleDateString("vi-VN")}</p>
            </div>
            <button type="button" disabled={busyId !== null} onClick={() => void withdraw(item.invitationId)} className="text-xs font-bold text-rose-700 disabled:opacity-50 cursor-pointer">Rút lời mời</button>
          </div>)}
        </section>

        <section className="lg:col-span-5 rounded-2xl border border-stone-200 bg-white p-6 h-fit space-y-4" aria-labelledby="invite-child-title">
          <h2 id="invite-child-title" className="font-heading text-base font-bold text-neutral-900 flex items-center gap-2"><UserPlus className="size-5 text-emerald-600" /> Liên kết thêm con</h2>
          <p className="text-xs text-neutral-500">Nhập email tài khoản học sinh của con. Học sinh cần chấp nhận lời mời trên cổng học sinh.</p>
          <form onSubmit={invite} className="space-y-3">
            <label htmlFor="child-email" className="block text-xs font-bold text-neutral-700">Email học sinh</label>
            <input id="child-email" type="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="con@example.com" className="w-full rounded-xl border border-stone-200 bg-stone-50 px-3.5 py-2.5 text-sm focus:outline-emerald-500" />
            <button type="submit" disabled={busyId !== null} className="w-full rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-50 cursor-pointer">{busyId === "invite" ? "Đang gửi..." : "Gửi lời mời"}</button>
          </form>
        </section>
      </div>

      <RevokeLinkModal
        link={linkToRevoke}
        studentName={linkToRevoke ? names[linkToRevoke.studentId] : undefined}
        isOpen={linkToRevoke !== null}
        onClose={() => setLinkToRevoke(null)}
        onConfirm={handleConfirmRevoke}
      />
    </main>
  );
}
