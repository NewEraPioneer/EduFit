package vn.edufit.iam.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, UUID> {

  Optional<UserSocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

  List<UserSocialAccount> findByUserId(UUID userId);

  boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
}
