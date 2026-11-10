package vn.edufit.scheduling.api;

import java.util.UUID;

/** Implemented by payment; a completed lesson must have its tuition reserved. */
public interface SessionFundingGuard {
  void requireFunded(UUID sessionId);
}
