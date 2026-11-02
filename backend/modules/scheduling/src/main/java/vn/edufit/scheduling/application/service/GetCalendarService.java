package vn.edufit.scheduling.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;

/**
 * Service xử lý tra cứu lịch học (Calendar View) cho người dùng trong một khoảng thời gian.
 */
@Service
@RequiredArgsConstructor
public class GetCalendarService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;

  /**
   * Tra cứu lịch học theo danh tính người dùng hiện tại, tự động ánh xạ profile và kiểm tra quyền phụ huynh.
   */
  @Transactional(readOnly = true)
  public List<TutoringSession> getCalendarSessions(CurrentUser actor, Instant from, Instant to, UUID requestedStudentId) {
    UUID profileId = schedulingSecurityResolver.resolveCalendarProfileId(actor, requestedStudentId);
    return sessionRepository.findCalendarSessions(profileId, from, to);
  }

  /**
   * Tương thích ngược với các lệnh gọi truyền trực tiếp Profile ID.
   */
  @Transactional(readOnly = true)
  public List<TutoringSession> getCalendarSessions(UUID profileOrUserId, Instant from, Instant to) {
    return sessionRepository.findCalendarSessions(profileOrUserId, from, to);
  }
}
