package vn.edufit.scheduling.infra.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.scheduling.infra.persistence.entity.TutoringSessionJpaEntity;
import vn.edufit.scheduling.infra.persistence.mapper.SessionMapper;
import vn.edufit.scheduling.infra.persistence.repository.SpringDataTutoringSessionRepository;

/**
 * Repository Adapter hiện thực hóa interface {@link TutoringSessionDomainRepository} của tầng Domain.
 *
 * <p>Theo nguyên lý Dependency Inversion của Clean Architecture:
 * Class này đóng vai trò là một Outbound Adapter, kết nối giữa nhu cầu lưu trữ của Domain
 * và công nghệ Spring Data JPA bên dưới.
 */
@Component
@RequiredArgsConstructor
public class TutoringSessionRepositoryAdapter implements TutoringSessionDomainRepository {

  private final SpringDataTutoringSessionRepository springDataRepository;
  private final SessionMapper sessionMapper;

  @Override
  public Optional<TutoringSession> findById(UUID sessionId) {
    return springDataRepository.findById(sessionId)
        .map(sessionMapper::toDomain);
  }

  @Override
  public Optional<TutoringSession> findByIdForUpdate(UUID sessionId) {
    return springDataRepository.findByIdForUpdate(sessionId)
        .map(sessionMapper::toDomain);
  }

  @Override
  public TutoringSession save(TutoringSession session) {
    TutoringSessionJpaEntity entity = sessionMapper.toJpaEntity(session);
    TutoringSessionJpaEntity saved = springDataRepository.saveAndFlush(entity);
    return sessionMapper.toDomain(saved);
  }

  @Override
  public List<TutoringSession> findExpiredProposals(Instant now) {
    return springDataRepository.findExpiredProposals(now)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public List<TutoringSession> findCalendarSessions(UUID userId, Instant from, Instant to) {
    return springDataRepository.findCalendarSessions(userId, from, to)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public List<TutoringSession> findTutorBusySlots(UUID tutorId, Instant from, Instant to) {
    return springDataRepository.findTutorBusySlots(tutorId, from, to)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public int countCompletedSessions(UUID classId) {
    return springDataRepository.countCompletedSessions(classId);
  }

  @Override
  public int countCompletedSessionsBetween(UUID tutorId, UUID studentId) {
    return springDataRepository.countCompletedSessionsBetween(tutorId, studentId);
  }

  @Override
  public List<TutoringSession> findUpcomingSessions(Instant windowStart, Instant windowEnd) {
    return springDataRepository.findUpcomingSessions(windowStart, windowEnd)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public List<TutoringSession> findOverdueOutcomeSessions(Instant cutoff) {
    return springDataRepository.findOverdueOutcomeSessions(cutoff)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public List<TutoringSession> findScheduledSessionsByClass(UUID classId, Instant from, Instant to) {
    return springDataRepository.findScheduledSessionsByClass(classId, from, to)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public List<TutoringSession> findByClassId(UUID classId, Instant from, Instant to) {
    if (classId == null) {
      return List.of();
    }
    List<TutoringSessionJpaEntity> entities;
    if (from == null && to == null) {
      entities = springDataRepository.findByClassIdOrderByStartAtAsc(classId);
    } else if (from != null && to == null) {
      entities = springDataRepository.findByClassIdAndStartAtGreaterThanEqualOrderByStartAtAsc(classId, from);
    } else if (from == null && to != null) {
      entities = springDataRepository.findByClassIdAndStartAtLessThanEqualOrderByStartAtAsc(classId, to);
    } else {
      entities = springDataRepository.findByClassIdAndStartAtGreaterThanEqualAndStartAtLessThanEqualOrderByStartAtAsc(classId, from, to);
    }
    return entities.stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public List<TutoringSession> findByClassIds(java.util.Collection<UUID> classIds, Instant from, Instant to) {
    if (classIds == null || classIds.isEmpty()) return List.of();
    List<TutoringSessionJpaEntity> entities;
    if (from == null && to == null) {
      entities = springDataRepository.findByClassIdInOrderByStartAtAsc(classIds);
    } else if (from != null && to == null) {
      entities = springDataRepository.findByClassIdInAndStartAtGreaterThanEqualOrderByStartAtAsc(classIds, from);
    } else if (from == null && to != null) {
      entities = springDataRepository.findByClassIdInAndStartAtLessThanEqualOrderByStartAtAsc(classIds, to);
    } else {
      entities = springDataRepository.findByClassIdInAndStartAtGreaterThanEqualAndStartAtLessThanEqualOrderByStartAtAsc(classIds, from, to);
    }
    return entities.stream()
        .map(sessionMapper::toDomain)
        .toList();
  }

  @Override
  public long countCompletedSessions() {
    return springDataRepository.countByStatus("COMPLETED");
  }
}

