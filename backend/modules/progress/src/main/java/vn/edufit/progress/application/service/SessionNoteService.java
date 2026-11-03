package vn.edufit.progress.application.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionTimelineView;
import vn.edufit.shared.auth.CurrentUser;

@Service
@RequiredArgsConstructor
public class SessionNoteService {

  private final SessionFacade sessionFacade;
  private final LearningPlanDomainRepository planRepository;
  private final ProgressAccessGuard accessGuard;
  private final SessionNoteTimelineAssembler timelineAssembler;

  @Transactional(readOnly = true)
  public Page<SessionNoteTimelineView> getClassNotesTimeline(
      CurrentUser actor,
      UUID classId,
      Instant from,
      Instant to,
      int page,
      int size
  ) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
    ViewerAudience audience =
        accessGuard.requireMemberOrParentAccess(actor, cls, "Bạn không có quyền xem ghi chú buổi học của lớp này");

    List<SessionTimelineView> sessions = sessionFacade.findSessionsByClassId(classId, from, to);
    if (sessions.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
    }

    // Loại trừ các buổi học đã hủy không có biên bản ghi chú sư phạm
    List<SessionNoteTimelineView> combined = timelineAssembler
        .assemble(sessions, planRepository.findByClassId(classId).orElse(null), audience)
        .stream()
        .filter(n -> !"CANCELLED".equalsIgnoreCase(n.status()) || n.recordedAt() != null)
        .toList();

    int start = Math.min(page * size, combined.size());
    int end = Math.min(start + size, combined.size());
    return new PageImpl<>(combined.subList(start, end), PageRequest.of(page, size), combined.size());
  }
}
