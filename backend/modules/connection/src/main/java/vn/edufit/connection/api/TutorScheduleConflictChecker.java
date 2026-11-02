package vn.edufit.connection.api;

import java.util.List;
import java.util.UUID;

/**
 * Service Provider Interface (SPI) for checking schedule conflicts against active tutoring sessions.
 *
 * <p>Architectural rationale:
 * The connection module cannot depend on scheduling due to cyclic Maven dependency constraints.
 * This SPI allows the connection gatekeeper and inbox queries to verify schedule conflicts
 * without violating module boundaries or creating circular dependencies.
 */
public interface TutorScheduleConflictChecker {

  /**
   * Checks whether any of the candidate recurring slots conflict with the tutor's scheduled sessions.
   *
   * @param tutorId      the tutor profile ID
   * @param desiredSlots the candidate recurring slot representations
   * @return true if a conflict exists, false otherwise
   */
  boolean hasConflictWithScheduledSessions(UUID tutorId, List<String> desiredSlots);
}
