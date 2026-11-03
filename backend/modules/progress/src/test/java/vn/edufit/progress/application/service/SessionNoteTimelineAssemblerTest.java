package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.model.HomeworkStatus;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.SelfRating;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.model.SessionSelfRating;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.progress.domain.repository.SessionSelfRatingDomainRepository;
import vn.edufit.scheduling.api.dto.SessionTimelineView;

class SessionNoteTimelineAssemblerTest {

  private static final String PARENT_MESSAGE = "Con cần ngủ sớm hơn trước buổi học";

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();

  private SessionSelfRatingDomainRepository selfRatingRepository;
  private SessionNoteTimelineAssembler assembler;

  @BeforeEach
  void setUp() {
    SessionNoteDomainRepository noteRepository = mock(SessionNoteDomainRepository.class);
    SessionFeedbackDomainRepository feedbackRepository = mock(SessionFeedbackDomainRepository.class);
    selfRatingRepository = mock(SessionSelfRatingDomainRepository.class);
    assembler = new SessionNoteTimelineAssembler(noteRepository, feedbackRepository, selfRatingRepository);

    UUID tutorUserId = UUID.randomUUID();
    when(feedbackRepository.findBySessionIdIn(any())).thenReturn(List.of(new SessionFeedback(
        UUID.randomUUID(), sessionId, classId, HomeworkStatus.NONE, List.of(), PARENT_MESSAGE, tutorUserId, now, false,
        List.of(new MilestoneAssessment(UUID.randomUUID(), UUID.randomUUID(), sessionId, IndependenceLevel.INDEPENDENT,
            null, null, null, tutorUserId, now)))));
    when(selfRatingRepository.findBySessionIdIn(any())).thenReturn(List.of(new SessionSelfRating(
        UUID.randomUUID(), sessionId, classId, UUID.randomUUID(), SelfRating.CONFIDENT, UUID.randomUUID(), now, now)));
  }

  private SessionNoteTimelineView assembleFor(ViewerAudience audience) {
    return assembler.assemble(
        List.of(new SessionTimelineView(sessionId, classId, now.minusSeconds(5400), now, "COMPLETED", now)),
        null,
        audience).getFirst();
  }

  @ParameterizedTest
  @EnumSource(ViewerAudience.class)
  @DisplayName("Lời nhắn riêng chỉ trả về cho phụ huynh, gia sư, admin; cảm nhận 1 chạm không trả về cho gia sư")
  void assemble_filtersPrivateFieldsByAudience(ViewerAudience audience) {
    SessionNoteTimelineView row = assembleFor(audience);

    assertThat(row.feedback().parentMessage()).isEqualTo(audience == ViewerAudience.STUDENT ? null : PARENT_MESSAGE);
    assertThat(row.selfRating()).isEqualTo(audience == ViewerAudience.TUTOR ? null : SelfRating.CONFIDENT.value());
    if (audience == ViewerAudience.TUTOR) {
      verify(selfRatingRepository, never()).findBySessionIdIn(any());
    }
  }
}
