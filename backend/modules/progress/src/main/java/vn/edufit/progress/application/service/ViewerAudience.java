package vn.edufit.progress.application.service;

/**
 * Vai trò của người đang xem dữ liệu tiến độ một lớp, quyết định phần riêng tư nào được trả về (mục 3.12).
 */
public enum ViewerAudience {
  ADMIN,
  TUTOR,
  STUDENT,
  PARENT;

  /** Lời nhắn riêng cho phụ huynh: chỉ phụ huynh, gia sư viết ra nó và admin; học sinh không bao giờ thấy. */
  public boolean seesParentMessage() {
    return this != STUDENT;
  }

  /** Cảm nhận 1 chạm của học sinh: học sinh, phụ huynh và admin; tách khỏi gia sư để không gắn với điểm chấm. */
  public boolean seesSelfRating() {
    return this != TUTOR;
  }
}
