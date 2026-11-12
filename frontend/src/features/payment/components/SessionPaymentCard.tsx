"use client";
import { useEffect, useState, type FormEvent } from "react";
import { paymentApi, money, type PaymentSession } from "../api/paymentApi";
import {
  issueCategories,
  paymentStatus,
  paymentNextAction,
} from "../paymentPresentation";
interface Props {
  session: PaymentSession;
  disabled: boolean;
  execute: (work: () => Promise<unknown>) => Promise<boolean>;
}
export function SessionPaymentCard({ session: s, disabled, execute }: Props) {
  const [mode, setMode] = useState<
    "confirm" | "dispute" | "report" | "comment" | "complete" | null
  >(null);
  const [text, setText] = useState("");
  const [category, setCategory] = useState("OTHER");
  const [evidence, setEvidence] = useState("");
  const [amount, setAmount] = useState(String(s.amount));
  const [messageId, setMessageId] = useState("");
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const participant = s.canActAsLearner || s.canActAsTutor;
  const pending = s.status === "FUNDED" || s.status === "AWAITING_CONFIRMATION";
  const afterEnd = now >= new Date(s.endAt).getTime();
  const deadline = s.reviewDeadline
    ? new Date(s.reviewDeadline).getTime()
    : null;
  const within = deadline === null || now < deadline;
  const canConfirm =
    participant &&
    pending &&
    s.sessionStatus === "COMPLETED" &&
    afterEnd &&
    deadline !== null &&
    within &&
    !(s.canActAsTutor ? s.tutorAccepted : s.learnerAccepted);
  const canDispute = participant && pending && afterEnd && within;
  const canReport =
    s.canSubmitProposal === true &&
    s.status === "DISPUTED" &&
    !!s.disputeDeadline &&
    now < new Date(s.disputeDeadline).getTime();
  const hasCase = ["DISPUTED", "AWAITING_ADMIN", "ADMIN_REVIEW"].includes(
    s.status,
  );
  const canComplete =
    s.canActAsTutor && s.sessionStatus === "SCHEDULED" && afterEnd;
  const tutorNet = s.amount - Math.floor((s.amount * s.commissionBps) / 10000);
  const button =
    "rounded-lg border border-line-strong px-3 py-2 text-sm font-medium disabled:opacity-50 hover:bg-paper";
  function open(next: typeof mode) {
    setMode(next);
    setText("");
    setEvidence("");
    if (next === "comment") setMessageId(crypto.randomUUID());
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    const ok = await execute(() => {
      if (mode === "confirm") return paymentApi.confirm(s.sessionId);
      if (mode === "complete") return paymentApi.completeSession(s.sessionId);
      if (mode === "dispute")
        return paymentApi.dispute(s.sessionId, text, category, evidence);
      if (mode === "comment")
        return paymentApi.comment(s.sessionId, messageId, text);
      return paymentApi.report(s.sessionId, Number(amount), text);
    });
    if (ok) setMode(null);
  }
  const submitLabel =
    mode === "confirm"
      ? "Ghi nhận xác nhận"
      : mode === "complete"
        ? "Xác nhận kết thúc"
        : mode === "dispute"
          ? "Gửi vấn đề"
          : mode === "comment"
            ? "Gửi phản hồi"
            : "Gửi đề xuất";
  return (
    <article className="rounded-2xl border border-line bg-surface p-5 space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-medium text-ink">Buổi học với {s.tutorName}</h3>
          <p className="mt-1 text-sm text-muted">
            {new Date(s.startAt).toLocaleString("vi-VN")}
          </p>
        </div>
        <span className="rounded-full bg-action-soft px-3 py-1 text-xs font-medium text-action-strong">
          {paymentStatus(s)}
        </span>
      </header>
      <div className="grid gap-3 rounded-xl bg-paper p-4 sm:grid-cols-2">
        <div>
          <p className="text-xs text-muted">Học phí buổi học</p>
          <strong className="text-2xl">{money(s.amount)}</strong>
        </div>
        <div className="text-sm">
          <p>
            Gia sư dự kiến nhận <strong>{money(tutorNet)}</strong>
          </p>
          <p className="text-xs text-muted">
            Phí nền tảng {s.commissionBps / 100}% đã gồm trong học phí.
          </p>
        </div>
      </div>
      <p className="text-sm text-ink-2">{paymentNextAction(s)}</p>
      {s.payerId && (
        <p className="text-xs text-muted">
          Người trả tiền: {s.isPayer ? "Bạn" : "Ví đã cấp học phí cho buổi này"}
          . Xác nhận việc học và báo vấn đề không thay đổi người sở hữu tiền.
        </p>
      )}
      {pending && s.reviewDeadline && (
        <div className="rounded-xl border border-brand-line bg-brand-soft p-4 text-sm">
          <p className="font-medium">
            Hạn báo vấn đề: {new Date(s.reviewDeadline).toLocaleString("vi-VN")}
          </p>
          <p>
            {within
              ? "Không có vấn đề được báo, hệ thống sẽ thanh toán sau thời điểm này. Hai bên xác nhận vẫn giữ đủ 24 giờ phản hồi."
              : "Đã hết hạn báo vấn đề. Đang chờ hệ thống tất toán."}
          </p>
        </div>
      )}
      {pending && (
        <p className="text-sm">
          Phản hồi việc học: người học{" "}
          {s.learnerAccepted ? "đã ghi nhận" : "chưa xác nhận"} · gia sư{" "}
          {s.tutorAccepted ? "đã ghi nhận" : "chưa xác nhận"}.
        </p>
      )}
      {s.disputeReason && (
        <section
          aria-label="Hồ sơ vấn đề"
          className="space-y-3 rounded-xl border border-action-line bg-action-soft p-4 text-sm"
        >
          <h4 className="font-medium">Một hồ sơ chung cho buổi học này</h4>
          <p>
            <strong>{issueCategories[s.disputeCategory ?? "OTHER"]}:</strong>{" "}
            {s.disputeReason}
          </p>
          {s.disputeEvidence && (
            <p className="whitespace-pre-wrap break-words">
              Thông tin/bằng chứng ban đầu: {s.disputeEvidence}
            </p>
          )}
          {s.status === "DISPUTED" && (
            <p>
              Hạn hai bên phản hồi:{" "}
              {s.disputeDeadline &&
                new Date(s.disputeDeadline).toLocaleString("vi-VN")}
              . Quá 48 giờ chưa thống nhất sẽ chuyển Admin; tiền tiếp tục được
              giữ.
            </p>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            <p>
              <strong>Người trả tiền:</strong>{" "}
              {s.learnerAmount === null
                ? "Chưa đề xuất mức giải quyết"
                : money(s.learnerAmount)}
              <br />
              {s.learnerReport}
            </p>
            <p>
              <strong>Gia sư:</strong>{" "}
              {s.tutorAmount === null
                ? "Chưa đề xuất mức giải quyết"
                : money(s.tutorAmount)}
              <br />
              {s.tutorReport}
            </p>
          </div>
          {(s.comments ?? []).map((message) => (
            <div key={message.id} className="border-t border-action-line pt-2">
              <p className="text-xs text-muted">
                {message.side === "TUTOR"
                  ? "Gia sư"
                  : message.side === "PAYER"
                    ? "Người trả tiền"
                    : "Học sinh"}{" "}
                · {new Date(message.created_at).toLocaleString("vi-VN")}
              </p>
              <p className="whitespace-pre-wrap break-words">
                {message.message}
              </p>
            </div>
          ))}
          {participant && !s.canSubmitProposal && hasCase && (
            <p>
              Học sinh có thể bổ sung thông tin. Đề xuất số tiền do người trả
              tiền và gia sư thực hiện.
            </p>
          )}
        </section>
      )}
      {s.status === "SETTLED" && (
        <p className="rounded-xl bg-success-soft p-4 text-sm text-success-strong">
          Đã thanh toán {money(s.settledAmount)}; gia sư nhận{" "}
          {money(
            s.settledAmount -
              Math.floor((s.settledAmount * s.commissionBps) / 10000),
          )}
          .
          {s.autoAccepted &&
            " Tự tất toán sau đủ thời hạn phản hồi, không có vấn đề được báo."}
        </p>
      )}
      {s.resolutionNote && (
        <p className="rounded-xl bg-paper p-4 text-sm">
          Quyết định Admin: {s.resolutionNote}
          {s.resolvedAt &&
            ` · ${new Date(s.resolvedAt).toLocaleString("vi-VN")}`}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        {canComplete && (
          <button
            className={button}
            disabled={disabled}
            onClick={() => open("complete")}
          >
            Ghi nhận buổi học hoàn thành
          </button>
        )}
        {canConfirm && (
          <button
            className={button}
            disabled={disabled}
            onClick={() => open("confirm")}
          >
            {s.canActAsTutor ? "Ghi nhận đã dạy" : "Xác nhận đã học"}
          </button>
        )}
        {canDispute && (
          <button
            className={button}
            disabled={disabled}
            onClick={() => open("dispute")}
          >
            Báo vấn đề
          </button>
        )}
        {canReport && (
          <button
            className={button}
            disabled={disabled}
            onClick={() => open("report")}
          >
            Đề xuất cách giải quyết
          </button>
        )}
        {participant && hasCase && (
          <button
            className={button}
            disabled={disabled}
            onClick={() => open("comment")}
          >
            Bổ sung thông tin
          </button>
        )}
      </div>
      {mode && (
        <form
          onSubmit={submit}
          className="space-y-4 rounded-xl border border-action-line p-4"
        >
          <p className="text-sm font-medium">
            {mode === "confirm"
              ? "Chỉ xác nhận việc học đã diễn ra. Học phí tiếp tục được giữ đến hết thời hạn phản hồi."
              : mode === "complete"
                ? "Xác nhận buổi học đã kết thúc. Người học sẽ có đủ 24 giờ phản hồi trước khi học phí được thanh toán."
                : mode === "dispute"
                  ? "Báo vấn đề và tạm giữ học phí"
                  : mode === "comment"
                    ? "Bổ sung thông tin cho hồ sơ chung"
                    : "Đề xuất mức học phí hợp lý; Admin xem xét trước khi xử lý tiền."}
          </p>
          {mode === "dispute" && (
            <>
              <label className="block text-sm">
                Bạn gặp vấn đề gì?
                <select
                  className="mt-1 w-full rounded-lg border p-2"
                  value={category}
                  onChange={(event) => setCategory(event.target.value)}
                >
                  {Object.entries(issueCategories).map(([id, label]) => (
                    <option key={id} value={id}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-sm">
                Bằng chứng (mô tả hoặc đường dẫn)
                <textarea
                  className="mt-1 w-full rounded-lg border p-2"
                  maxLength={4000}
                  value={evidence}
                  onChange={(event) => setEvidence(event.target.value)}
                />
              </label>
            </>
          )}
          {mode === "report" && (
            <label className="block text-sm">
              Học phí đề xuất (trước phí nền tảng)
              <input
                className="mt-1 w-full rounded-lg border p-2"
                required
                type="number"
                min={0}
                max={s.amount}
                step={1}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
            </label>
          )}
          {["dispute", "report", "comment"].includes(mode) && (
            <label className="block text-sm">
              {mode === "dispute"
                ? "Nội dung vấn đề"
                : mode === "comment"
                  ? "Thông tin bổ sung"
                  : "Nội dung đề xuất"}
              <textarea
                className="mt-1 w-full rounded-lg border p-2"
                required
                maxLength={mode === "dispute" ? 2000 : 4000}
                value={text}
                onChange={(event) => setText(event.target.value)}
              />
            </label>
          )}
          <div className="flex gap-2">
            <button
              disabled={disabled}
              className="rounded-lg bg-action px-4 py-2 text-sm font-medium disabled:opacity-50"
            >
              {submitLabel}
            </button>
            <button
              type="button"
              disabled={disabled}
              className={button}
              onClick={() => setMode(null)}
            >
              Quay lại
            </button>
          </div>
        </form>
      )}
    </article>
  );
}
