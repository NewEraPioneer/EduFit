package vn.edufit.progress.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.edufit.progress.application.dto.SessionFeedbackView;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.model.SessionSelfRating;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.progress.domain.repository.SessionSelfRatingDomainRepository;
import vn.edufit.scheduling.api.dto.SessionTimelineView;

/**
 * Ghép buổi học (Scheduling) với nhận xét ({@code session_note}), phản hồi có cấu trúc và cảm nhận 1 chạm của học sinh
 * thành dòng sổ liên lạc, lọc phần riêng tư theo {@link ViewerAudience}. Batch query theo danh sách buổi học để tránh N+1.
 */
@Component
public class SessionNoteTimelineAssembler {

  private final SessionNoteDomainRepository noteRepository;
  private final SessionFeedbackDomainRepository feedbackRepository;
  private final SessionSelfRatingDomainRepository selfRatingRepository;

  public SessionNoteTimelineAssembler(
      SessionNoteDomainRepository noteRepository,
      SessionFeedbackDomainRepository feedbackRepository,
      SessionSelfRatingDomainRepository selfRatingRepository) {
    this.noteRepository = noteRepository;
    this.feedbackRepository = feedbackRepository;
    this.selfRatingRepository = selfRatingRepository;
  }

  /**
   * @param plan     lộ trình của lớp (có thể null) để lấy tên cột mốc trong phản hồi.
   * @param audience vai trò người xem, quyết định có trả lời nhắn riêng cho phụ huynh và cảm nhận của học sinh không.
   * @return một dòng cho mỗi buổi học, giữ nguyên thứ tự đầu vào; note/feedback null nếu chưa có.
   */
  public List<SessionNoteTimelineView> assemble(
      List<SessionTimelineView> sessions, LearningPlan plan, ViewerAudience audience) {
    if (sessions.isEmpty()) return List.of();
    List<UUID> sessionIds = sessions.stream().map(SessionTimelineView::sessionId).toList();
    Map<UUID, SessionNote> notes = noteRepository.findBySessionIdIn(sessionIds).stream()
        .collect(Collectors.toMap(SessionNote::getSessionId, Function.identity()));
    Map<UUID, SessionFeedback> feedbacks = feedbackRepository.findBySessionIdIn(sessionIds).stream()
        .collect(Collectors.toMap(SessionFeedback::getSessionId, Function.identity()));
    Map<UUID, Integer> selfRatings = audience.seesSelfRating()
        ? selfRatingRepository.findBySessionIdIn(sessionIds).stream()
            .collect(Collectors.toMap(SessionSelfRating::getSessionId, r -> r.getRating().value()))
        : Map.of();
    Map<UUID, String> milestoneTitles = plan == null
        ? Map.of()
        : plan.getMilestones().stream().collect(Collectors.toMap(Milestone::getMilestoneId, Milestone::getTitle));

    return sessions.stream()
        .map(s -> {
          SessionNote note = notes.get(s.sessionId());
          return new SessionNoteTimelineView(
              s.sessionId(),
              s.classId(),
              s.startAt(),
              s.endAt(),
              s.status(),
              note != null ? note.getNoteText() : null,
              note != null ? note.getCreatedAt() : null,
              SessionFeedbackView.from(feedbacks.get(s.sessionId()), milestoneTitles, audience.seesParentMessage()),
              selfRatings.get(s.sessionId())
          );
        })
        .toList();
  }
}
