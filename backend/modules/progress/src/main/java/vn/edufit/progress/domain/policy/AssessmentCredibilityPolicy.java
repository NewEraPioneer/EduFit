package vn.edufit.progress.domain.policy;

import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Độ tin cậy của điểm chấm do gia sư nhập (mục 3.11):
 * <ol>
 *   <li>Tăng từ {@value #JUMP_REQUIRING_EVIDENCE} mức trở lên so với lần chấm trước trong một buổi thì bắt buộc có
 *       "đúng x/y bài" hoặc một câu bằng chứng. Lần chấm đầu là điểm xuất phát nên không bị kiểm tra.</li>
 *   <li>Không được chấm mức 4 khi cột mốc chưa tick đủ tiêu chí (cột mốc cũ không có tiêu chí được miễn).</li>
 * </ol>
 */
public final class AssessmentCredibilityPolicy {

  public static final int JUMP_REQUIRING_EVIDENCE = 2;

  private AssessmentCredibilityPolicy() {}

  /**
   * @param previous  mức của lần chấm gần nhất trước buổi này, {@code null} nếu đây là điểm xuất phát.
   * @param milestone cột mốc sau khi đã áp dụng tick tiêu chí của buổi này.
   */
  public static void check(IndependenceLevel previous, MilestoneAssessment assessment, Milestone milestone) {
    IndependenceLevel next = assessment.getLevel();
    if (next == IndependenceLevel.MASTERY && !milestone.allCriteriaChecked()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Cột mốc \"" + milestone.getTitle() + "\" chưa tick đủ tiêu chí nên chưa thể chấm mức 4");
    }
    if (previous != null && next.value() - previous.value() >= JUMP_REQUIRING_EVIDENCE && !assessment.hasEvidence()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Cột mốc \"" + milestone.getTitle() + "\" tăng từ mức " + previous.value() + " lên mức " + next.value()
              + " trong một buổi: cần nhập \"đúng x/y bài\" hoặc một câu bằng chứng");
    }
  }
}
