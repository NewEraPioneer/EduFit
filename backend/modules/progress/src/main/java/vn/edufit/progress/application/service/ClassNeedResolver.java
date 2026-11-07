package vn.edufit.progress.application.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Nhu cầu học (môn, khối, mục tiêu) của một lớp: dùng chung cho mẫu lộ trình, danh mục chủ đề và bản nháp AI.
 */
@Component
public class ClassNeedResolver {

  private final ProfileFacade profileFacade;

  public ClassNeedResolver(ProfileFacade profileFacade) {
    this.profileFacade = profileFacade;
  }

  /**
   * @param purpose phần cuối câu báo lỗi, ví dụ "để chọn mẫu lộ trình".
   * @return nhu cầu học có đủ môn và khối.
   */
  public LearningNeedView requireSubjectAndLevel(ConnectionFacade.ClassDetailView cls, String purpose) {
    return (cls.needId() != null ? profileFacade.findLearningNeed(cls.needId()) : Optional.<LearningNeedView>empty())
        .filter(n -> n.subjectId() != null && n.educationLevelId() != null)
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_OPERATION, "Lớp học chưa có nhu cầu học đủ môn và khối " + purpose));
  }
}
