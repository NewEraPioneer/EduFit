package vn.edufit.iam.application;

import java.time.Instant;

/**
 * Thông tin công khai về một mã OTP vừa gửi, dùng để giao diện hiển thị hạn mã và đếm ngược nút "Gửi lại".
 *
 * @param email             email nhận mã (đã chuẩn hóa)
 * @param codeExpiresAt     thời điểm mã hết hạn
 * @param resendAvailableAt thời điểm sớm nhất được yêu cầu gửi lại mã
 */
public record OtpChallengeView(String email, Instant codeExpiresAt, Instant resendAvailableAt) {}
