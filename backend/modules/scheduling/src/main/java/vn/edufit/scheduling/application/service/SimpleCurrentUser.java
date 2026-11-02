package vn.edufit.scheduling.application.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import vn.edufit.shared.auth.CurrentUser;

/**
 * Triển khai nhẹ của {@link CurrentUser} phục vụ việc tương thích ngược và kiểm thử.
 */
public record SimpleCurrentUser(
    UUID userId,
    String email,
    Set<String> roles
) implements CurrentUser {

  public SimpleCurrentUser {
    Objects.requireNonNull(userId, "userId không được null");
    email = email != null ? email : "user@edufit.vn";
    roles = roles != null ? Set.copyOf(roles) : Collections.emptySet();
  }

  public static SimpleCurrentUser of(UUID userId, String... roles) {
    return new SimpleCurrentUser(userId, "user@edufit.vn", roles != null ? Set.of(roles) : Collections.emptySet());
  }

  @Override
  public UUID getUserId() {
    return userId;
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public Set<String> getRoles() {
    return roles;
  }

  @Override
  public boolean hasRole(String role) {
    return roles.contains(role);
  }
}
