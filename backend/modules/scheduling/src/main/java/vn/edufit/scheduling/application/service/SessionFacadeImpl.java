package vn.edufit.scheduling.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.CompletedSessionStatsView;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.scheduling.api.dto.TutorBusySlotView;
import vn.edufit.scheduling.domain.policy.ConflictPolicy;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Hiện thực hóa interface {@link SessionFacade} - Bề mặt công khai duy nhất cho các module khác.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionFacadeImpl implements SessionFacade {

  private final TutoringSessionDomainRepository sessionRepository;

  @Override
  public Optional<SessionSummaryView> findSessionById(UUID sessionId) {
    return sessionRepository.findById(sessionId)
        .map(s -> new SessionSummaryView(
            s.getSessionId(),
            s.getClassId(),
            s.getTutorId(),
            s.getStudentId(),
            s.getStartAt(),
            s.getEndAt(),
            s.getMode().name(),
            s.getStatus().name(),
            s.isLateCancel(),
            s.getCreatedAt(), s.getOutcomeRecordedAt()
        ));
  }

  @Override
  public List<TutorBusySlotView> findBusySlotsByTutorId(UUID tutorId, Instant from, Instant to) {
    return sessionRepository.findTutorBusySlots(tutorId, from, to)
        .stream()
        .map(s -> new TutorBusySlotView(
            s.getSessionId(),
            s.getTutorId(),
            s.getStartAt(),
            s.getEndAt()
        ))
        .toList();
  }

  @Override
  public CompletedSessionStatsView getCompletedStatsByClassId(UUID classId) {
    int count = sessionRepository.countCompletedSessions(classId);
    return new CompletedSessionStatsView(classId, null, null, count, 0, null);
  }

  @Override
  public int countCompletedSessionsBetween(UUID tutorId, UUID studentId) {
    return sessionRepository.countCompletedSessionsBetween(tutorId, studentId);
  }

  @Override
  public boolean hasActiveSessionAt(UUID tutorId, Instant startAt, Instant endAt) {
    return sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)
        .stream()
        .anyMatch(s -> ConflictPolicy.isOverlapping(startAt, endAt, s.getStartAt(), s.getEndAt()));
  }

  @Override
  public List<vn.edufit.scheduling.api.dto.SessionTimelineView> findSessionsByClassId(UUID classId, Instant from, Instant to) {
    if (classId == null) return List.of();
    return sessionRepository.findByClassId(classId, from, to)
        .stream()
        .map(s -> new vn.edufit.scheduling.api.dto.SessionTimelineView(
            s.getSessionId(),
            s.getClassId(),
            s.getStartAt(),
            s.getEndAt(),
            s.getStatus().name(), s.getOutcomeRecordedAt()
        ))
        .toList();
  }

  @Override
  public java.util.Map<UUID, vn.edufit.scheduling.api.dto.ClassSessionStatsView> getSessionStatsByClassIds(List<UUID> classIds, Instant from, Instant to) {
    if (classIds == null || classIds.isEmpty()) return java.util.Map.of();

    List<vn.edufit.scheduling.domain.model.TutoringSession> sessions = sessionRepository.findByClassIds(classIds, from, to);
    Instant now = Instant.now();

    java.util.Map<UUID, List<vn.edufit.scheduling.domain.model.TutoringSession>> grouped = sessions.stream()
        .collect(java.util.stream.Collectors.groupingBy(vn.edufit.scheduling.domain.model.TutoringSession::getClassId));

    java.util.Map<UUID, vn.edufit.scheduling.api.dto.ClassSessionStatsView> result = new java.util.HashMap<>();
    for (UUID cid : classIds) {
      List<vn.edufit.scheduling.domain.model.TutoringSession> classSessions = grouped.getOrDefault(cid, List.of());

      int completed = 0;
      int absent = 0;
      int cancelled = 0;
      int upcoming = 0;
      Instant nextSession = null;
      Instant lastSession = null;

      for (vn.edufit.scheduling.domain.model.TutoringSession s : classSessions) {
        switch (s.getStatus()) {
          case COMPLETED -> completed++;
          case ABSENT -> absent++;
          case CANCELLED -> cancelled++;
          case SCHEDULED -> {
            if (s.getStartAt().isAfter(now)) {
              upcoming++;
              if (nextSession == null || s.getStartAt().isBefore(nextSession)) {
                nextSession = s.getStartAt();
              }
            }
          }
          default -> {}
        }

        if (s.getStartAt().isBefore(now)) {
          if (lastSession == null || s.getStartAt().isAfter(lastSession)) {
            lastSession = s.getStartAt();
          }
        }
      }

      result.put(cid, new vn.edufit.scheduling.api.dto.ClassSessionStatsView(
          cid, completed, absent, cancelled, upcoming, nextSession, lastSession
      ));
    }

    return result;
  }

  @Override
  public long countCompletedSessions() {
    return sessionRepository.countCompletedSessions();
  }
}

