package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.PlanTemplateViews.PlanTemplateView;
import vn.edufit.progress.application.dto.PlanTemplateViews.SavePlanTemplateCommand;
import vn.edufit.progress.application.dto.PlanTemplateViews.TemplateMilestoneView;
import vn.edufit.progress.domain.model.PlanTemplate;
import vn.edufit.progress.domain.repository.PlanTemplateDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

class PlanTemplateServiceTest {

  private static final int MATH = 1;
  private static final int GRADE_9 = 9;
  private static final int EXAM_10 = 3;

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID needId = UUID.randomUUID();

  private PlanTemplateDomainRepository repository;
  private ProfileFacade profileFacade;
  private PlanTemplateService service;
  private CurrentUser tutor;
  private CurrentUser admin;

  @BeforeEach
  void setUp() {
    repository = mock(PlanTemplateDomainRepository.class);
    profileFacade = mock(ProfileFacade.class);
    ConnectionFacade connectionFacade = mock(ConnectionFacade.class);
    service = new PlanTemplateService(repository, new ProgressAccessGuard(connectionFacade, profileFacade), profileFacade,
        Clock.fixed(now, ZoneId.of("UTC")));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);
    when(tutor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    admin = mock(CurrentUser.class);
    when(admin.hasRole("ADMIN")).thenReturn(true);
    when(admin.getUserId()).thenReturn(UUID.randomUUID());

    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, UUID.randomUUID(), tutorId, needId, "ACTIVE", now, null, 200000L)));
    LearningNeedView need = mock(LearningNeedView.class);
    when(need.subjectId()).thenReturn(MATH);
    when(need.educationLevelId()).thenReturn(GRADE_9);
    when(need.goalTagId()).thenReturn(EXAM_10);
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(need));
  }

  private static List<TemplateMilestoneView> milestones(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> new TemplateMilestoneView("Cột mốc " + (i + 1), null, 1, 3, List.of("Tiêu chí " + (i + 1))))
        .toList();
  }

  private PlanTemplate template(UUID owner, Integer goal, String title) {
    return new PlanTemplate(UUID.randomUUID(), owner, MATH, GRADE_9, goal, title, null,
        milestones(3).stream().map(m -> new PlanTemplate.TemplateMilestone(
            m.title(), m.description(), m.weight(), m.suggestedSessions(), m.criteria())).toList(),
        UUID.randomUUID(), 0, now, now);
  }

  @Test
  @DisplayName("Gia sư lập lộ trình cho lớp: chỉ mẫu hợp môn × khối × mục tiêu, mẫu đúng mục tiêu và mẫu riêng lên trước")
  void list_filtersByClassNeed() {
    PlanTemplate generic = template(null, null, "Toán 9 tổng quát");
    PlanTemplate systemExam = template(null, EXAM_10, "Toán 9 ôn thi vào 10");
    PlanTemplate mineExam = template(tutorId, EXAM_10, "Mẫu của tôi");
    PlanTemplate otherGoal = template(null, EXAM_10 + 1, "Toán 9 nâng cao");
    when(repository.findVisibleTo(tutorId)).thenReturn(List.of(generic, systemExam, mineExam, otherGoal));

    List<PlanTemplateView> views = service.list(tutor, classId);

    assertThat(views).extracting(PlanTemplateView::title)
        .containsExactly("Mẫu của tôi", "Toán 9 ôn thi vào 10", "Toán 9 tổng quát");
    assertThat(views.getFirst().mine()).isTrue();
    assertThat(views.get(1).system()).isTrue();
  }

  @Test
  @DisplayName("Gia sư lưu lộ trình của lớp thành mẫu riêng: danh mục lấy từ nhu cầu học của lớp")
  void create_tutorTemplateFromClass() {
    PlanTemplateView view = service.create(tutor, new SavePlanTemplateCommand(
        classId, null, null, null, "Mẫu của tôi", null, milestones(3), null));

    ArgumentCaptor<PlanTemplate> captor = ArgumentCaptor.forClass(PlanTemplate.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getOwnerTutorId()).isEqualTo(tutorId);
    assertThat(captor.getValue().getSubjectId()).isEqualTo(MATH);
    assertThat(captor.getValue().getGoalTagId()).isEqualTo(EXAM_10);
    assertThat(view.mine()).isTrue();
  }

  @Test
  @DisplayName("Admin tạo mẫu hệ thống với danh mục nhập trực tiếp; mẫu phải đúng hình dạng 3–8 cột mốc, 2–6 buổi")
  void create_adminSystemTemplateAndShapeRules() {
    PlanTemplateView view = service.create(admin, new SavePlanTemplateCommand(
        null, MATH, GRADE_9, EXAM_10, "Toán 9 ôn thi vào 10", null, milestones(4), null));
    assertThat(view.system()).isTrue();

    assertThatThrownBy(() -> service.create(admin, new SavePlanTemplateCommand(
        null, MATH, GRADE_9, null, "Ngắn", null, milestones(2), null)))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("từ 3 đến 8");
    assertThatThrownBy(() -> service.create(admin, new SavePlanTemplateCommand(
        null, MATH, GRADE_9, null, "Buổi lạ", null,
        List.of(new TemplateMilestoneView("A", null, 1, 7, List.of("x")),
            new TemplateMilestoneView("B", null, 1, 3, List.of("x")),
            new TemplateMilestoneView("C", null, 1, 3, List.of("x"))), null)))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("từ 2 đến 6 buổi");
    assertThatThrownBy(() -> service.create(admin, new SavePlanTemplateCommand(
        null, null, GRADE_9, null, "Thiếu môn", null, milestones(3), null)))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("môn học và khối lớp");
  }

  @Test
  @DisplayName("Quyền sửa/xóa: mẫu hệ thống chỉ admin; mẫu riêng chỉ chủ sở hữu; sai phiên bản bị từ chối")
  void updateAndDelete_permissionsAndVersion() {
    PlanTemplate system = template(null, EXAM_10, "Hệ thống");
    PlanTemplate others = template(UUID.randomUUID(), EXAM_10, "Của gia sư khác");
    PlanTemplate mine = template(tutorId, EXAM_10, "Của tôi");
    when(repository.findById(system.getTemplateId())).thenReturn(Optional.of(system));
    when(repository.findById(others.getTemplateId())).thenReturn(Optional.of(others));
    when(repository.findById(mine.getTemplateId())).thenReturn(Optional.of(mine));
    SavePlanTemplateCommand edit = new SavePlanTemplateCommand(
        null, MATH, GRADE_9, EXAM_10, "Đã sửa", null, milestones(3), 0);

    assertThatThrownBy(() -> service.update(tutor, system.getTemplateId(), edit))
        .isInstanceOf(ForbiddenOperationException.class);
    assertThatThrownBy(() -> service.delete(tutor, others.getTemplateId()))
        .isInstanceOf(ForbiddenOperationException.class);
    assertThatThrownBy(() -> service.update(admin, mine.getTemplateId(), edit))
        .isInstanceOf(ForbiddenOperationException.class);
    assertThatThrownBy(() -> service.update(tutor, mine.getTemplateId(), new SavePlanTemplateCommand(
        null, MATH, GRADE_9, EXAM_10, "Đã sửa", null, milestones(3), 5)))
        .isInstanceOf(ResourceConflictException.class);
    verify(repository, never()).delete(any());

    assertThat(service.update(tutor, mine.getTemplateId(), edit).title()).isEqualTo("Đã sửa");
    assertThat(service.update(admin, system.getTemplateId(), edit).title()).isEqualTo("Đã sửa");
    service.delete(tutor, mine.getTemplateId());
    verify(repository).delete(mine.getTemplateId());
  }

  @Test
  @DisplayName("Học sinh/phụ huynh không dùng mẫu lộ trình")
  void list_rejectsNonTutor() {
    CurrentUser parent = mock(CurrentUser.class);
    when(parent.hasRole("PARENT")).thenReturn(true);
    when(parent.getUserId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> service.list(parent, null)).isInstanceOf(ForbiddenOperationException.class);
  }
}
