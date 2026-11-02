package vn.edufit.iam.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.iam.infra.persistence.AccountRepository;

/**
 * Triển khai interface {@link IamFacade} phục vụ các module nghiệp vụ khác gọi sang.
 */
@Component
public class IamFacadeImpl implements IamFacade {

  private final AccountRepository accountRepository;

  public IamFacadeImpl(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  public Optional<UserSummaryView> findUserSummaryById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return accountRepository.findById(id).map(this::toSummary);
  }

  @Override
  public Optional<UserSummaryView> findUserSummaryByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    return accountRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(this::toSummary);
  }

  @Override
  public Map<UUID, UserSummaryView> findUserSummariesByIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    return accountRepository.findAllById(ids).stream()
        .map(this::toSummary)
        .collect(Collectors.toMap(UserSummaryView::id, u -> u));
  }

  private UserSummaryView toSummary(vn.edufit.iam.infra.persistence.Account account) {
    return new UserSummaryView(account.getId(), account.getEmail(), account.getFullName(),
        account.getRole(), account.getStatus());
  }

  @Override
  public boolean existsById(UUID id) {
    if (id == null) {
      return false;
    }
    return accountRepository.existsById(id);
  }

  @Override
  public long countTotalUsers() {
    return accountRepository.count();
  }

  @Override
  public long countUsersByRole(vn.edufit.iam.api.AccountRole role) {
    if (role == null) {
      return 0L;
    }
    return accountRepository.countByRole(role);
  }

  @Override
  public List<UUID> findActiveAdminIds() { return accountRepository.findActiveAdminIds(); }
}
