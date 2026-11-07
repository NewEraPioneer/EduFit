package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.ai.api.AiResponse;
import vn.edufit.ai.api.AiUsageQuery;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.PlanDraftView;
import vn.edufit.progress.application.dto.PlanDraftView.DraftMilestoneView;
import vn.edufit.progress.domain.model.Topic;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

class PlanDraftServiceTest {

  private static final int MATH = 2;
  private static final int GRADE_9 = 9;

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID needId = UUID.randomUUID();

  private AiGateway aiGateway;
  private AiUsageQuery aiUsageQuery;
  private TopicDomainRepository topicRepository;
  private PlanDraftService service;
  private CurrentUser tutor;
  private Topic algebra;

  @BeforeEach
  void setUp() {
    aiGateway = mock(AiGateway.class);
    aiUsageQuery = mock(AiUsageQuery.class);
    topicRepository = mock(TopicDomainRepository.class);
    ProfileFacade profileFacade = mock(ProfileFacade.class);
    ConnectionFacade connectionFacade = mock(ConnectionFacade.class);
    service = new PlanDraftService(aiGateway, aiUsageQuery, new ProgressAccessGuard(connectionFacade, profileFacade),
        new ClassNeedResolver(profileFacade), topicRepository, Clock.fixed(now, ZoneId.of("UTC")));

    tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);
    when(tutor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);

    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, UUID.randomUUID(), tutorId, needId, "ACTIVE", now, null, 200000L)));
    LearningNeedView need = mock(LearningNeedView.class);
    when(need.subjectId()).thenReturn(MATH);
    when(need.educationLevelId()).thenReturn(GRADE_9);
    when(need.subjectName()).thenReturn("Toán");
    when(need.educationLevelName()).thenReturn("Lớp 9");
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(need));

    algebra = Topic.createNew(MATH, GRADE_9, "Đại số", 0, UUID.randomUUID(), now);
    when(topicRepository.findActiveBySubjectAndLevel(MATH, GRADE_9)).thenReturn(List.of(algebra));
  }

  private static String milestonesJson(int count, String topic) {
    return IntStream.range(0, count)
        .mapToObj(i -> "{\"title\":\"Cột mốc " + (i + 1) + "\",\"description\":null,\"weight\":9,"
            + "\"suggestedSessions\":1,\"criteria\":[\"Tiêu chí " + (i + 1) + "\"],\"topic\":"
            + (topic == null ? "null" : "\"" + topic + "\"") + "}")
        .collect(Collectors.joining(",", "{\"milestones\":[", "]}"));
  }

  private void givenAiReturns(String text) {
    when(aiGateway.complete(any())).thenReturn(new AiResponse(text, 100, null, true));
  }

  @Test
  @DisplayName("AI trả bản nháp hợp lệ: trọng số và số buổi bị kẹp vào giới hạn, tên chủ đề khớp danh mục được đổi thành ID")
  void draft_validOutput() {
    when(aiUsageQuery.countRequestsSince(eq(tutorUserId), eq(AiFeature.LEARNING_PLAN_DRAFT), any())).thenReturn(3L);
    givenAiReturns("```json\n" + milestonesJson(3, "đại số") + "\n```");

    PlanDraftView view = service.draft(tutor, classId);

    assertThat(view.unavailableReason()).isNull();
    assertThat(view.remainingQuota()).isEqualTo(PlanDraftService.HOURLY_QUOTA - 4);
    assertThat(view.milestones()).hasSize(3);
    DraftMilestoneView first = view.milestones().getFirst();
    assertThat(first.weight()).isEqualTo(5);
    assertThat(first.suggestedSessions()).isEqualTo(2);
    assertThat(first.topicId()).isEqualTo(algebra.getTopicId());

    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiGateway).complete(captor.capture());
    assertThat(captor.getValue().userPrompt()).contains("Toán", "Lớp 9", "Đại số");
  }

  @Test
  @DisplayName("Chủ đề AI bịa ra không thuộc danh mục thì bỏ trống topicId")
  void draft_unknownTopicIgnored() {
    givenAiReturns(milestonesJson(3, "Lượng giác"));

    PlanDraftView view = service.draft(tutor, classId);

    assertThat(view.milestones()).extracting(DraftMilestoneView::topicId).containsOnlyNulls();
  }

  @Test
  @DisplayName("Hết lượt trong 1 giờ: không gọi AI, trả lý do kèm gợi ý dùng mẫu")
  void draft_quotaExhausted() {
    when(aiUsageQuery.countRequestsSince(any(), any(), any())).thenReturn((long) PlanDraftService.HOURLY_QUOTA);

    PlanDraftView view = service.draft(tutor, classId);

    assertThat(view.milestones()).isEmpty();
    assertThat(view.unavailableReason()).isEqualTo(PlanDraftService.QUOTA_EXHAUSTED);
    verify(aiGateway, never()).complete(any());
  }

  @Test
  @DisplayName("AI lỗi: trả lý do gợi ý dùng mẫu, không ném lỗi")
  void draft_aiFailure() {
    when(aiGateway.complete(any())).thenThrow(new IllegalStateException("timeout"));

    PlanDraftView view = service.draft(tutor, classId);

    assertThat(view.milestones()).isEmpty();
    assertThat(view.unavailableReason()).isEqualTo(PlanDraftService.AI_UNAVAILABLE);
  }

  @Test
  @DisplayName("Đầu ra sai hình dạng (không phải JSON, quá ít cột mốc, cột mốc thiếu tiêu chí) bị loại")
  void draft_invalidOutput() {
    givenAiReturns("Xin lỗi, tôi không thể giúp.");
    assertThat(service.draft(tutor, classId).unavailableReason()).isEqualTo(PlanDraftService.AI_INVALID);

    assertThat(PlanDraftService.parse(milestonesJson(2, null), List.of())).isEmpty();
    assertThat(PlanDraftService.parse(milestonesJson(9, null), List.of())).isEmpty();
    assertThat(PlanDraftService.parse(
        milestonesJson(3, null).replace("[\"Tiêu chí 2\"]", "[]"), List.of())).isEmpty();
  }

  @Test
  @DisplayName("Người không phải gia sư của lớp không gợi ý được lộ trình")
  void draft_onlyTutorOfClass() {
    CurrentUser other = mock(CurrentUser.class);
    when(other.hasRole("TUTOR")).thenReturn(true);
    when(other.getUserId()).thenReturn(UUID.randomUUID());

    assertThatThrownBy(() -> service.draft(other, classId)).isInstanceOf(ForbiddenOperationException.class);
    verify(aiGateway, never()).complete(any());
  }
}
