package vn.edufit.profile.api.fit;

/**
 * Người đọc nhãn so khớp. Cùng một phép so nhưng câu chữ phải theo góc nhìn người xem:
 * học sinh/phụ huynh đọc "gia sư có đáp ứng mình không", gia sư đọc "mình có đáp ứng học sinh không".
 */
public enum FitAudience {
  /** Học sinh/phụ huynh đang xem gia sư (Discovery, AI Match, form gửi yêu cầu). */
  LEARNER,
  /** Gia sư đang xem yêu cầu kết nối của học sinh/phụ huynh (hộp thư yêu cầu). */
  TUTOR
}
