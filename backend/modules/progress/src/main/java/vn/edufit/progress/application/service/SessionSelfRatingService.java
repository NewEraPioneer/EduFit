package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.progress.domain.model.SelfRating;
import vn.edufit.progress.domain.model.SessionSelfRating;
import vn.edufit.progress.domain.repository.SessionSelfRatingDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Cảm nhận 1 chạm của học sinh sau buổi học (mục 3.11). Chỉ học sinh của lớp, chỉ buổi đã hoàn thành;
 * chọn lại thì ghi đè. Không ảnh hưởng điểm chấm hay % tiến độ.
 */
@Service
public class SessionSelfRatingService {

  private final SessionSelfRatingDomainRepository ratingRepository;
  private final SessionFacade sessionFacade;
  private final ProgressAccessGuard accessGuard;
  private final Clock clock;

  public SessionSelfRatingService(
      SessionSelfRatingDomainRepository ratingRepository,
      SessionFacade sessionFacade,
      ProgressAccessGuard accessGuard,
      Clock clock
  ) {
    this.ratingRepository = ratingRepository;
    this.sessionFacade = sessionFacade;
    this.accessGuard = accessGuard;
    this.clock = Objects.requireNonNull(clock);
  }

  /** @return giá trị cảm nhận đã lưu (1–3). */
  @Transactional
  public int rate(CurrentUser actor, UUID sessionId, Integer value) {
    SessionSummaryView session = sessionFacade.findSessionById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(session.classId());
    accessGuard.requireStudentOfClass(actor, cls);
    if (!"COMPLETED".equals(session.status())) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Chỉ chọn cảm nhận cho buổi học đã hoàn thành");
    }
    if (value == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần chọn một cảm nhận về buổi học");
    }

    SelfRating rating = SelfRating.fromValue(value);
    Instant now = clock.instant();
    SessionSelfRating saved = ratingRepository.findBySessionId(sessionId)
        .map(existing -> {
          existing.change(rating, now);
          return ratingRepository.save(existing);
        })
        .orElseGet(() -> ratingRepository.save(new SessionSelfRating(
            UUID.randomUUID(), sessionId, cls.classId(), cls.studentId(), rating, actor.getUserId(), now, now)));
    return saved.getRating().value();
  }
}
