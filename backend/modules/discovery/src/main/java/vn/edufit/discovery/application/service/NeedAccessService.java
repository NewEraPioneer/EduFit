package vn.edufit.discovery.application.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.domain.model.EducationLevelOrder;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/** Kiểm tra quyền dùng nhu cầu học ở Discovery và cung cấp thứ tự khối lớp cho chấm điểm. */
@Service
public class NeedAccessService {

  private final ProfileFacade profileFacade;
  private final ConnectionFacade connectionFacade;

  public NeedAccessService(ProfileFacade profileFacade, ConnectionFacade connectionFacade) {
    this.profileFacade = profileFacade;
    this.connectionFacade = connectionFacade;
  }

  /** Nhu cầu chỉ dùng được bởi chính học sinh hoặc phụ huynh có liên kết đã xác nhận. */
  public LearningNeedView authorizedNeed(CurrentUser actor, UUID needId) {
    if (actor == null || actor.getUserId() == null) {
      throw new ForbiddenOperationException("Cần đăng nhập để dùng nhu cầu học.");
    }
    LearningNeedView need = profileFacade.findLearningNeed(needId)
        .orElseThrow(() -> EntityNotFoundException.of("LearningNeed", needId));
    boolean owner = actor.hasRole("STUDENT") && actor.getUserId().equals(need.studentUserId());
    boolean linkedParent = !owner && actor.hasRole("PARENT")
        && connectionFacade.hasConfirmedParentLink(actor.getUserId(), need.studentId());
    if (!owner && !linkedParent) {
      throw new ForbiddenOperationException("Bạn không có quyền với nhu cầu học này.");
    }
    if ("ARCHIVED".equals(need.status())) {
      throw new InvalidOperationException("Nhu cầu học đã được lưu trữ.");
    }
    return need;
  }

  public EducationLevelOrder educationLevelOrder() {
    return new EducationLevelOrder(profileFacade.findEducationLevelsForMatching().stream()
        .map(EducationLevelOrderDto::levelId).toList());
  }
}
