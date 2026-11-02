package vn.edufit.scheduling.infra.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.ClassScheduleStatusProvider;
import vn.edufit.scheduling.infra.persistence.repository.SpringDataTutoringSessionRepository;

/**
 * Adapter triển khai SPI {@link ClassScheduleStatusProvider} từ module Connection.
 *
 * <p>Cung cấp số lượng buổi học và trạng thái xếp lịch (SCHEDULED, COMPLETED, PROPOSED)
 * cho danh sách các lớp học mà không làm vi phạm ranh giới kiến trúc module.
 */
@Component
@RequiredArgsConstructor
public class ClassScheduleStatusProviderAdapter implements ClassScheduleStatusProvider {

  private final SpringDataTutoringSessionRepository sessionRepository;

  @Override
  public Map<UUID, ClassScheduleInfo> getScheduleInfoBatch(Collection<UUID> classIds) {
    if (classIds == null || classIds.isEmpty()) {
      return Map.of();
    }

    List<UUID> validClassIds = classIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (validClassIds.isEmpty()) {
      return Map.of();
    }

    List<Object[]> rows = sessionRepository.countSessionsByClassIdsAndStatuses(validClassIds);
    Map<UUID, Long> scheduledMap = new HashMap<>();
    Map<UUID, Long> completedMap = new HashMap<>();

    for (Object[] row : rows) {
      if (row == null || row.length < 3 || row[0] == null) {
        continue;
      }
      UUID cid = (UUID) row[0];
      String status = (String) row[1];
      long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
      if ("SCHEDULED".equals(status)) {
        scheduledMap.put(cid, count);
      } else if ("COMPLETED".equals(status)) {
        completedMap.put(cid, count);
      }
    }

    Map<UUID, ClassScheduleInfo> result = new HashMap<>();
    for (UUID cid : validClassIds) {
      int scheduled = (int) (long) scheduledMap.getOrDefault(cid, 0L);
      int completed = (int) (long) completedMap.getOrDefault(cid, 0L);
      int total = completed + scheduled;
      String status = scheduled > 0 ? "SCHEDULED" : (completed > 0 ? "COMPLETED" : "PROPOSED");
      result.put(cid, new ClassScheduleInfo(status, completed, scheduled, total));
    }
    return result;
  }
}
