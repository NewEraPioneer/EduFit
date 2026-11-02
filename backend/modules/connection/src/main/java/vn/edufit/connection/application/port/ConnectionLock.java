package vn.edufit.connection.application.port;

import java.util.UUID;

/** Transaction-scoped locks: quota before pair; student before pair before row. */
public interface ConnectionLock {
  void lockStudent(UUID studentId);
  void lockInvitationPair(UUID firstUserId, UUID secondUserId);
  void lockInvitationQuota(UUID userId);
}
