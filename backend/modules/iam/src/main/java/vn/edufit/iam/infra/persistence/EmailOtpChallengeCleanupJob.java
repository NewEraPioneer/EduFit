package vn.edufit.iam.infra.persistence;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.application.OtpPolicy;

/**
 * Dọn các thử thách OTP đã hết hạn hoặc đã dùng (kể cả đăng ký không bao giờ được xác thực).
 *
 * <p>Giữ lại ít nhất một cửa sổ gửi mã cộng thêm một ngày để giới hạn số lần gửi vẫn có hiệu lực.
 */
@Component
public class EmailOtpChallengeCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(EmailOtpChallengeCleanupJob.class);
  private static final Duration RETENTION_MARGIN = Duration.ofDays(1);

  private final EmailOtpChallengeRepository challengeRepository;
  private final OtpPolicy otpPolicy;
  private final Clock clock;

  public EmailOtpChallengeCleanupJob(
      EmailOtpChallengeRepository challengeRepository, OtpPolicy otpPolicy, Clock clock) {
    this.challengeRepository = challengeRepository;
    this.otpPolicy = otpPolicy;
    this.clock = clock;
  }

  @Scheduled(cron = "${edufit.auth.otp.cleanup-cron}")
  @Transactional
  public void purgeStaleChallenges() {
    int deleted = challengeRepository.deleteStaleBefore(
        clock.instant().minus(otpPolicy.sendWindow()).minus(RETENTION_MARGIN));
    if (deleted > 0) {
      log.info("[IAM OTP] Đã dọn {} thử thách OTP cũ", deleted);
    }
  }
}
