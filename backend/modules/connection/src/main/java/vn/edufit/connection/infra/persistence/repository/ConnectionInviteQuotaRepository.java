package vn.edufit.connection.infra.persistence.repository;

import vn.edufit.connection.infra.persistence.entity.*;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionInviteQuotaRepository extends JpaRepository<ConnectionInviteQuota, UUID> {}
