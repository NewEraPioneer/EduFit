package vn.edufit.profile.api.fit;

/**
 * Kết quả so một tiêu chí của nhu cầu học với gia sư/gói dạy.
 *
 * @param criterion tiêu chí
 * @param score     mức khớp 0–100 (100: khớp hoàn toàn)
 * @param matched   true khi đủ để hiển thị ✔, false khi hiển thị ⚠
 * @param label     mô tả ngắn cho người dùng, không kèm biểu tượng
 */
public record FitCheck(FitCriterion criterion, int score, boolean matched, String label) {

  public FitCheck {
    if (score < 0 || score > 100) {
      throw new IllegalArgumentException("Fit score must be between 0 and 100.");
    }
  }

  static FitCheck match(FitCriterion criterion, String label) {
    return new FitCheck(criterion, 100, true, label);
  }

  static FitCheck partial(FitCriterion criterion, int score, String label) {
    return new FitCheck(criterion, score, false, label);
  }

  static FitCheck mismatch(FitCriterion criterion, String label) {
    return new FitCheck(criterion, 0, false, label);
  }
}
