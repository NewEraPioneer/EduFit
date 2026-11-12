import { money, type PaymentSession } from "./api/paymentApi";

export const issueCategories: Record<string, string> = {
  DURATION: "Thiếu thời lượng",
  NO_SHOW: "Không tham gia buổi học",
  TECHNICAL: "Sự cố kỹ thuật",
  QUALITY: "Chất lượng buổi học",
  OTHER: "Vấn đề khác",
};
export function paymentStatus(session: PaymentSession) {
  switch (session.status) {
    case "UNFUNDED":
      return tuitionDue(session) ? "Cần thanh toán" : "Chờ thu học phí";
    case "FUNDED":
      return "Học phí đã được giữ";
    case "AWAITING_CONFIRMATION":
      return "Chờ phản hồi sau buổi học";
    case "DISPUTED":
      return "Đang xử lý vấn đề";
    case "AWAITING_ADMIN":
      return "Chờ Admin duyệt thống nhất";
    case "ADMIN_REVIEW":
      return "Chờ Admin phân xử";
    case "SETTLED":
      return "Đã thanh toán";
    case "REFUNDED":
    case "EXPIRED":
      return "Đã hoàn về ví người trả";
  }
}
/**
 * Buổi học đến hạn thanh toán mà hệ thống chưa thu được: trước buổi học (từ mốc 24 giờ) hoặc sau
 * buổi học (đã hoàn thành). Backend tính cờ này để mốc chỉ nằm một nơi.
 */
export function tuitionDue(session: PaymentSession) {
  return session.status === "UNFUNDED" && session.due;
}

/** Buổi chưa thu học phí: khi nào hệ thống thu và người học cần làm gì. Hệ thống không hủy buổi học. */
function unfundedAction(session: PaymentSession) {
  const tutor = session.canActAsTutor;
  if (session.sessionStatus === "COMPLETED")
    return tutor
      ? "Buổi học đã hoàn thành nhưng chưa thu được học phí. Hệ thống tự thu khi ví người học đủ tiền. Sau đó học phí về ví bạn khi hết 24 giờ phản hồi."
      : `Buổi học đã hoàn thành, còn nợ ${money(session.amount)} học phí. Hãy nạp tiền. Hệ thống tự thu ngay khi ví đủ.`;
  if (session.sessionStatus !== "SCHEDULED")
    return "Buổi học không diễn ra nên không thu học phí.";
  if (tuitionDue(session))
    return tutor
      ? "Đã đến hạn nhưng chưa thu được học phí vì ví người học chưa đủ. Hệ thống tự thu khi ví đủ tiền."
      : `Đã đến hạn thanh toán ${money(session.amount)}. Hãy nạp tiền. Hệ thống tự thu ngay khi ví đủ.`;
  const when = new Date(session.dueAt ?? session.startAt).toLocaleString("vi-VN");
  return tutor
    ? `Hệ thống tự thu học phí của buổi này lúc ${when}.`
    : `Hệ thống tự thu ${money(session.amount)} từ ví lúc ${when} (24 giờ trước giờ học). Hãy giữ đủ tiền trong ví.`;
}

export function paymentNextAction(session: PaymentSession) {
  if (session.status === "UNFUNDED") return unfundedAction(session);
  if (session.status === "DISPUTED")
    return "Hai bên bổ sung thông tin trong 48 giờ. Tiền tiếp tục được giữ.";
  if (session.status === "AWAITING_ADMIN" || session.status === "ADMIN_REVIEW")
    return "Admin đang xem hồ sơ. Tiền được giữ đến khi có quyết định; không tự chuyển hoặc hoàn khi hết hạn.";
  if (session.status === "SETTLED")
    return "Học phí đã vào ví gia sư sau khi trừ phí nền tảng.";
  if (session.status === "REFUNDED" || session.status === "EXPIRED")
    return "Tiền đã về số dư khả dụng của ví đã trả học phí.";
  if (session.sessionStatus !== "COMPLETED")
    return "Chờ ghi nhận buổi học hoàn thành. Nếu có vấn đề, hãy báo ngay trong chi tiết buổi học.";
  if (!session.reviewDeadline)
    return "Hệ thống đang ghi nhận thời hạn phản hồi. Học phí chưa được tất toán.";
  return "Bạn có đủ 24 giờ để phản hồi. Xác nhận đã học không làm mất quyền báo vấn đề trong thời hạn này.";
}
export function sessionPaymentHref(
  role: "STUDENT" | "PARENT" | "TUTOR",
  id: string,
) {
  const base =
    role === "TUTOR"
      ? "/tutor/earnings"
      : role === "PARENT"
        ? "/parent/billing"
        : "/student/payments";
  return `${base}/sessions/${encodeURIComponent(id)}`;
}
