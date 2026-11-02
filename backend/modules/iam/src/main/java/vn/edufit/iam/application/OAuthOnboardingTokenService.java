package vn.edufit.iam.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Quản lý mã lưu tạm thời thông tin người dùng OAuth đang trong bước chọn vai trò (Onboarding Role Selection).
 * Hạn sử dụng: 30 phút.
 */
@Service
public class OAuthOnboardingTokenService {

  private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

  private record TokenEntry(OAuthUserInfo userInfo, Instant expiresAt) {
    boolean isExpired(Instant now) {
      return now.isAfter(expiresAt);
    }
  }

  private final Map<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();

  /**
   * Lưu thông tin OAuth của người dùng mới và sinh mã onboardingToken ngẫu nhiên.
   */
  public String store(OAuthUserInfo userInfo) {
    cleanExpiredTokens();
    String token = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(TOKEN_TTL);
    tokenStore.put(token, new TokenEntry(userInfo, expiresAt));
    return token;
  }

  /**
   * Lấy thông tin OAuth bằng token và hủy token đó (chỉ sử dụng 1 lần).
   */
  public Optional<OAuthUserInfo> consume(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    TokenEntry entry = tokenStore.remove(token.trim());
    if (entry == null || entry.isExpired(Instant.now())) {
      return Optional.empty();
    }
    return Optional.of(entry.userInfo());
  }

  /**
   * Tra cứu thông tin mà không tiêu thụ token (để hiển thị preview nếu cần).
   */
  public Optional<OAuthUserInfo> peek(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    TokenEntry entry = tokenStore.get(token.trim());
    if (entry == null || entry.isExpired(Instant.now())) {
      return Optional.empty();
    }
    return Optional.of(entry.userInfo());
  }

  private void cleanExpiredTokens() {
    Instant now = Instant.now();
    tokenStore.entrySet().removeIf(e -> e.getValue().isExpired(now));
  }
}
