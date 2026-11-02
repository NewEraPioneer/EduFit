package vn.edufit.scheduling.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.TutoringSession;

/**
 * Cổng giao tiếp kho lưu trữ (Domain Repository Port) do Domain làm chủ.
 *
 * <p>Theo nguyên lý Dependency Inversion của Clean Architecture:
 * Tầng Domain định nghĩa nhu cầu lưu trữ thông qua interface này mà không biết bên dưới dùng
 * JPA, Hibernate hay MongoDB. Tầng Infrastructure sẽ hiện thực hóa interface này sau.
 */
public interface TutoringSessionDomainRepository {

  Optional<TutoringSession> findById(UUID sessionId);

  Optional<TutoringSession> findByIdForUpdate(UUID sessionId);

  TutoringSession save(TutoringSession session);

  List<TutoringSession> findExpiredProposals(Instant now);

  List<TutoringSession> findCalendarSessions(UUID userId, Instant from, Instant to);

  List<TutoringSession> findTutorBusySlots(UUID tutorId, Instant from, Instant to);

  int countCompletedSessions(UUID classId);

  int countCompletedSessionsBetween(UUID tutorId, UUID studentId);

  List<TutoringSession> findUpcomingSessions(Instant windowStart, Instant windowEnd);

  List<TutoringSession> findOverdueOutcomeSessions(Instant cutoff);

  List<TutoringSession> findScheduledSessionsByClass(UUID classId, Instant from, Instant to);

  List<TutoringSession> findByClassId(UUID classId, Instant from, Instant to);

  List<TutoringSession> findByClassIds(java.util.Collection<UUID> classIds, Instant from, Instant to);

  long countCompletedSessions();
}
