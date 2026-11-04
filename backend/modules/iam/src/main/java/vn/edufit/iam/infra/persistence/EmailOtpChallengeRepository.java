package vn.edufit.iam.infra.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository của bảng {@code email_otp_challenge}.
 */
@Repository
public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, UUID> {

  /**
   * Lấy thử thách chưa tiêu thụ của (mục đích, email) và khóa dòng để các lần nhập mã/gửi lại
   * đồng thời không vượt được giới hạn số lần.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT c FROM EmailOtpChallenge c
      WHERE c.purpose = :purpose
        AND c.email = :email
        AND c.consumedAt IS NULL
      """)
  Optional<EmailOtpChallenge> findActiveForUpdate(
      @Param("purpose") OtpPurpose purpose,
      @Param("email") String email
  );

  /**
   * Xóa thử thách đã hết hạn hoặc đã tiêu thụ trước mốc {@code cutoff}.
   */
  @Modifying
  @Query("""
      DELETE FROM EmailOtpChallenge c
      WHERE c.expiresAt < :cutoff
         OR c.consumedAt < :cutoff
      """)
  int deleteStaleBefore(@Param("cutoff") Instant cutoff);
}
