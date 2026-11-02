package vn.edufit.progress.application.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionTimelineView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ForbiddenOperationException;

@Service
@RequiredArgsConstructor
public class SessionNoteService {

  private final SessionNoteDomainRepository noteRepository;
  private final SessionFacade sessionFacade;
  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;

  @Transactional(readOnly = true)
  public Page<SessionNoteTimelineView> getClassNotesTimeline(
      CurrentUser actor,
      UUID classId,
      Instant from,
      Instant to,
      int page,
      int size
  ) {
    ConnectionFacade.ClassDetailView cls = connectionFacade.findClassById(classId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp học với ID: " + classId));

    requireAccess(actor, cls);

    // 1. Lấy danh sách session từ Scheduling
    List<SessionTimelineView> sessions = sessionFacade.findSessionsByClassId(classId, from, to);
    if (sessions.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
    }

    // 2. Batch lookup session_note từ Progress qua Domain Repository (tránh N+1)
    List<UUID> sessionIds = sessions.stream().map(SessionTimelineView::sessionId).toList();
    Map<UUID, SessionNote> noteMap = noteRepository.findBySessionIdIn(sessionIds).stream()
        .collect(Collectors.toMap(SessionNote::getSessionId, n -> n));

    // 3. Kết hợp session và note (loại trừ các buổi học đã hủy không có biên bản ghi chú sư phạm)
    List<SessionNoteTimelineView> combined = sessions.stream()
        .filter(s -> !"CANCELLED".equalsIgnoreCase(s.status()) || noteMap.containsKey(s.sessionId()))
        .map(s -> {
          SessionNote note = noteMap.get(s.sessionId());
          return new SessionNoteTimelineView(
              s.sessionId(),
              s.classId(),
              s.startAt(),
              s.endAt(),
              s.status(),
              note != null ? note.getNoteText() : null,
              note != null ? note.getCreatedAt() : null
          );
        })
        .toList();

    // 4. Phân trang
    int start = Math.min(page * size, combined.size());
    int end = Math.min(start + size, combined.size());
    List<SessionNoteTimelineView> pageContent = combined.subList(start, end);

    return new PageImpl<>(pageContent, PageRequest.of(page, size), combined.size());
  }

  private void requireAccess(CurrentUser actor, ConnectionFacade.ClassDetailView cls) {
    if (actor.hasRole("ADMIN")) return;

    if (actor.hasRole("TUTOR")) {
      UUID tutorId = profileFacade.getTutorIdByUserId(actor.getUserId());
      if (tutorId != null && tutorId.equals(cls.tutorId())) return;
    }

    if (actor.hasRole("STUDENT")) {
      UUID studentId = profileFacade.getStudentIdByUserId(actor.getUserId());
      if (studentId != null && studentId.equals(cls.studentId())) return;
    }

    if (actor.hasRole("PARENT")) {
      if (connectionFacade.hasConfirmedParentLink(actor.getUserId(), cls.studentId())) return;
    }

    throw new ForbiddenOperationException("Bạn không có quyền xem ghi chú buổi học của lớp này");
  }
}
