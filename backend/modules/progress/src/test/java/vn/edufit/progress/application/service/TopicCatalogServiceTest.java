package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.TopicViews.SaveTopicCommand;
import vn.edufit.progress.application.dto.TopicViews.TopicView;
import vn.edufit.progress.domain.model.Topic;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

class TopicCatalogServiceTest {

  private static final int MATH = 2;
  private static final int GRADE_9 = 9;

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID classId = UUID.randomUUID();
  private final UUID needId = UUID.randomUUID();

  private TopicDomainRepository repository;
  private TopicCatalogService service;
  private CurrentUser admin;
  private ConnectionFacade.ClassDetailView cls;

  @BeforeEach
  void setUp() {
    repository = mock(TopicDomainRepository.class);
    ProfileFacade profileFacade = mock(ProfileFacade.class);
    ConnectionFacade connectionFacade = mock(ConnectionFacade.class);
    service = new TopicCatalogService(repository, new ProgressAccessGuard(connectionFacade, profileFacade),
        new ClassNeedResolver(profileFacade), Clock.fixed(now, ZoneId.of("UTC")));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    admin = mock(CurrentUser.class);
    when(admin.hasRole("ADMIN")).thenReturn(true);
    when(admin.getUserId()).thenReturn(UUID.randomUUID());

    cls = new ConnectionFacade.ClassDetailView(classId, UUID.randomUUID(), UUID.randomUUID(), needId, "ACTIVE", now,
        null, 200000L);
    LearningNeedView need = mock(LearningNeedView.class);
    when(need.subjectId()).thenReturn(MATH);
    when(need.educationLevelId()).thenReturn(GRADE_9);
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(need));
  }

  private Topic topic(int subject, String name, boolean archived) {
    Topic t = Topic.createNew(subject, GRADE_9, name, 0, UUID.randomUUID(), now);
    if (archived) t.revise(name, 0, true, now);
    return t;
  }

  @Test
  @DisplayName("Chỉ admin quản lý danh mục chủ đề")
  void onlyAdmin() {
    CurrentUser tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);

    assertThatThrownBy(() -> service.listAll(tutor)).isInstanceOf(ForbiddenOperationException.class);
    assertThatThrownBy(() -> service.create(tutor, new SaveTopicCommand(MATH, GRADE_9, "Đại số", null, false, null)))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  @DisplayName("Tạo chủ đề: bắt buộc môn và khối, tên không trùng (không phân biệt hoa thường) trong cùng môn × khối")
  void create_validatesSubjectLevelAndUniqueName() {
    when(repository.findAll()).thenReturn(List.of(topic(MATH, "Đại số", false)));

    assertThatThrownBy(() -> service.create(admin, new SaveTopicCommand(null, GRADE_9, "Hình học", null, false, null)))
        .isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(() -> service.create(admin, new SaveTopicCommand(MATH, GRADE_9, " đại SỐ ", null, false, null)))
        .isInstanceOf(ResourceConflictException.class);

    TopicView created = service.create(admin, new SaveTopicCommand(MATH + 1, GRADE_9, "Đại số", 3, false, null));
    assertThat(created.subjectId()).isEqualTo(MATH + 1);
    assertThat(created.sortOrder()).isEqualTo(3);
  }

  @Test
  @DisplayName("Sửa chủ đề với phiên bản cũ báo xung đột")
  void update_staleVersion() {
    Topic existing = topic(MATH, "Đại số", false);
    when(repository.findById(existing.getTopicId())).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.update(admin, existing.getTopicId(),
        new SaveTopicCommand(null, null, "Đại số 9", null, false, existing.getVersion() + 1)))
        .isInstanceOf(ResourceConflictException.class);

    TopicView archived = service.update(admin, existing.getTopicId(),
        new SaveTopicCommand(null, null, "Đại số 9", null, true, existing.getVersion()));
    assertThat(archived.name()).isEqualTo("Đại số 9");
    assertThat(archived.archived()).isTrue();
  }

  @Test
  @DisplayName("Gắn chủ đề cho cột mốc: phải đúng môn × khối của lớp; chủ đề lưu trữ chỉ giữ được trên cột mốc cũ")
  void requireAssignable() {
    Topic other = topic(MATH + 1, "Văn bản", false);
    Topic retired = topic(MATH, "Thống kê", true);
    when(repository.findByIdIn(any())).thenAnswer(inv -> List.of(other, retired));

    assertThatThrownBy(() -> service.requireAssignable(cls, List.of(other.getTopicId()), Set.of()))
        .isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(() -> service.requireAssignable(cls, List.of(retired.getTopicId()), Set.of()))
        .isInstanceOf(InvalidOperationException.class);
    assertThatCode(() -> service.requireAssignable(cls, List.of(retired.getTopicId()), Set.of(retired.getTopicId())))
        .doesNotThrowAnyException();
  }
}
