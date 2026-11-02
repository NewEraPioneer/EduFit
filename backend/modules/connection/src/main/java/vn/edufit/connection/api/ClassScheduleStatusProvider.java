package vn.edufit.connection.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Service Provider Interface (SPI) for retrieving scheduling metrics and status for tutoring classes.
 *
 * <p>Architectural rationale:
 * The connection module cannot depend directly on scheduling due to cyclic Maven dependency constraints.
 * This SPI allows connection class queries to obtain scheduled session metrics without violating module boundaries.
 */
public interface ClassScheduleStatusProvider {

  record ClassScheduleInfo(String scheduleStatus, int completedCount, int scheduledCount, int totalCount) {
    public int sessionCount() {
      return completedCount;
    }

    public ClassScheduleInfo(String scheduleStatus, int sessionCount) {
      this(scheduleStatus, sessionCount, 0, sessionCount);
    }
  }

  /**
   * Batch retrieves schedule info for multiple classes (avoids N+1 query).
   *
   * @param classIds collection of class UUIDs
   * @return map of classId to ClassScheduleInfo
   */
  Map<UUID, ClassScheduleInfo> getScheduleInfoBatch(Collection<UUID> classIds);
}
