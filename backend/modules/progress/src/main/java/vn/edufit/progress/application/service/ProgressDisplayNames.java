package vn.edufit.progress.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;

/**
 * Tên hiển thị dùng chung của module Progress: tên học sinh, tên gia sư, tiêu đề lớp theo nhu cầu học.
 */
@Component
public class ProgressDisplayNames {

  static final String STUDENT_FALLBACK = "Học sinh";
  static final String TUTOR_FALLBACK = "Gia sư";

  private final ProfileFacade profileFacade;
  private final IamFacade iamFacade;

  public ProgressDisplayNames(ProfileFacade profileFacade, IamFacade iamFacade) {
    this.profileFacade = profileFacade;
    this.iamFacade = iamFacade;
  }

  public String studentName(UUID studentId) {
    return studentId == null ? STUDENT_FALLBACK : studentName(profileFacade.findStudentById(studentId).orElse(null));
  }

  public String studentName(StudentSummaryDto student) {
    if (student == null || student.userId() == null) return STUDENT_FALLBACK;
    return fullName(student.userId(), STUDENT_FALLBACK);
  }

  public String tutorName(UUID tutorId) {
    if (tutorId == null) return TUTOR_FALLBACK;
    TutorSummaryDto tutor = profileFacade.findTutorById(tutorId).orElse(null);
    if (tutor == null || tutor.userId() == null) return TUTOR_FALLBACK;
    return fullName(tutor.userId(), TUTOR_FALLBACK);
  }

  /** Tiêu đề lớp theo nhu cầu học, ví dụ "Luyện thi vào 10 · Toán · Lớp 9". */
  public static String needTitle(LearningNeedView need, String fallback) {
    if (need == null || need.goalTagName() == null) return fallback;
    List<String> parts = new ArrayList<>();
    parts.add(need.goalTagName());
    if (need.subjectName() != null) parts.add(need.subjectName());
    if (need.educationLevelName() != null) parts.add(need.educationLevelName());
    return String.join(" · ", parts);
  }

  private String fullName(UUID userId, String fallback) {
    return iamFacade.findUserSummaryById(userId).map(UserSummaryView::fullName).orElse(fallback);
  }
}
