import type { PaymentSession } from "./api/paymentApi";

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
      return "Chưa giữ học phí";
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
export function paymentNextAction(session: PaymentSession) {
  if (session.status === "UNFUNDED")
    return session.canFund
      ? "Kiểm tra học phí rồi giữ tiền từ ví cho buổi này."
      : "Chờ người trả tiền cấp học phí.";
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
