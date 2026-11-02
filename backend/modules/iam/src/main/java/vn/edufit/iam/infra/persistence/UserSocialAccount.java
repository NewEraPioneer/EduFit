package vn.edufit.iam.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA Entity đại diện cho liên kết tài khoản định danh mạng xã hội (Google, Zalo,...) với tài khoản EduFit.
 */
@Entity
@Table(name = "user_social_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "provider", nullable = false, length = 50)
  private String provider;

  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @Column(name = "email", length = 255)
  private String email;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static UserSocialAccount of(
      UUID userId,
      String provider,
      String providerUserId,
      String email,
      String avatarUrl
  ) {
    Objects.requireNonNull(userId, "userId không được phép null");
    Objects.requireNonNull(provider, "provider không được để trống");
    Objects.requireNonNull(providerUserId, "providerUserId không được để trống");

    UserSocialAccount account = new UserSocialAccount();
    account.userId = userId;
    account.provider = provider.trim().toUpperCase();
    account.providerUserId = providerUserId.trim();
    account.email = email != null ? Account.normalizeEmail(email) : null;
    account.avatarUrl = avatarUrl;
    return account;
  }

  public void updateProfile(String email, String avatarUrl) {
    if (email != null && !email.isBlank()) {
      this.email = Account.normalizeEmail(email);
    }
    if (avatarUrl != null && !avatarUrl.isBlank()) {
      this.avatarUrl = avatarUrl;
    }
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
