package vn.edufit.iam.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Tiện ích mã hóa sinh và băm One-Time Token (NFR-03).
 *
 * <ul>
 *   <li>Sinh mã ngẫu nhiên bảo mật 256-bit (32 bytes) trả về chuỗi Hex 64 ký tự.</li>
 *   <li>Băm mã token bằng thuật toán SHA-256 (64 ký tự Hex) để lưu vào CSDL.</li>
 * </ul>
 */
@Component
public class TokenCryptoService {

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Sinh chuỗi raw token ngẫu nhiên 256-bit dạng Hex (64 ký tự).
   */
  public String generateRawToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /**
   * Sinh mã OTP 6 chữ số ngẫu nhiên bảo mật (giữ các số 0 ở đầu).
   */
  public String generateOtpCode() {
    return String.format("%06d", secureRandom.nextInt(1_000_000));
  }

  /**
   * Băm mã OTP gắn với một thử thách: {@code SHA-256(challengeId + ":" + code)}.
   * {@code challengeId} đóng vai trò salt nên cùng một mã ở hai thử thách cho ra hai mã băm khác nhau.
   */
  public String hashOtpCode(UUID challengeId, String code) {
    if (challengeId == null) {
      throw new IllegalArgumentException("Mã thử thách không được để trống");
    }
    return hashToken(challengeId + ":" + (code == null ? "" : code.trim()));
  }

  /**
   * Băm chuỗi raw token bằng thuật toán SHA-256 thành chuỗi Hex 64 ký tự.
   */
  public String hashToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("Token không được để trống");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Không tìm thấy thuật toán SHA-256 trên JVM", e);
    }
  }
}
