package vn.edufit.scheduling.infra.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ClassScheduleStatusProvider.ClassScheduleInfo;
import vn.edufit.scheduling.infra.persistence.repository.SpringDataTutoringSessionRepository;

@ExtendWith(MockitoExtension.class)
class ClassScheduleStatusProviderAdapterTest {

  @Mock
  private SpringDataTutoringSessionRepository sessionRepository;

  private ClassScheduleStatusProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ClassScheduleStatusProviderAdapter(sessionRepository);
  }

  @Test
  void getScheduleInfoBatch_emptyInput_returnsEmptyMap() {
    Map<UUID, ClassScheduleInfo> result = adapter.getScheduleInfoBatch(Collections.emptyList());
    assertTrue(result.isEmpty());
    verifyNoInteractions(sessionRepository);
  }

  @Test
  void getScheduleInfoBatch_nullInput_returnsEmptyMap() {
    Map<UUID, ClassScheduleInfo> result = adapter.getScheduleInfoBatch(null);
    assertTrue(result.isEmpty());
    verifyNoInteractions(sessionRepository);
  }

  @Test
  void getScheduleInfoBatch_returnsCorrectStatusesAndCounts() {
    UUID classWithScheduled = UUID.randomUUID();
    UUID classWithCompletedOnly = UUID.randomUUID();
    UUID classWithoutSessions = UUID.randomUUID();

    List<Object[]> mockRows = List.of(
        new Object[] { classWithScheduled, "SCHEDULED", 4L },
        new Object[] { classWithScheduled, "COMPLETED", 2L },
        new Object[] { classWithCompletedOnly, "COMPLETED", 3L }
    );

    List<UUID> inputIds = List.of(classWithScheduled, classWithCompletedOnly, classWithoutSessions);
    when(sessionRepository.countSessionsByClassIdsAndStatuses(inputIds)).thenReturn(mockRows);

    Map<UUID, ClassScheduleInfo> result = adapter.getScheduleInfoBatch(inputIds);

    assertEquals(3, result.size());

    ClassScheduleInfo info1 = result.get(classWithScheduled);
    assertNotNull(info1);
    assertEquals("SCHEDULED", info1.scheduleStatus());
    assertEquals(2, info1.completedCount());
    assertEquals(4, info1.scheduledCount());
    assertEquals(6, info1.totalCount());
    assertEquals(2, info1.sessionCount());

    ClassScheduleInfo info2 = result.get(classWithCompletedOnly);
    assertNotNull(info2);
    assertEquals("COMPLETED", info2.scheduleStatus());
    assertEquals(3, info2.completedCount());
    assertEquals(0, info2.scheduledCount());
    assertEquals(3, info2.totalCount());
    assertEquals(3, info2.sessionCount());

    ClassScheduleInfo info3 = result.get(classWithoutSessions);
    assertNotNull(info3);
    assertEquals("PROPOSED", info3.scheduleStatus());
    assertEquals(0, info3.completedCount());
    assertEquals(0, info3.scheduledCount());
    assertEquals(0, info3.totalCount());
    assertEquals(0, info3.sessionCount());
  }

  @Test
  void getScheduleInfoBatch_withNullAndDuplicateElements_filtersCleanly() {
    UUID classId = UUID.randomUUID();
    List<UUID> inputWithNulls = new java.util.ArrayList<>();
    inputWithNulls.add(classId);
    inputWithNulls.add(null);
    inputWithNulls.add(classId);

    List<Object[]> mockRows = Collections.singletonList(
        new Object[] { classId, "SCHEDULED", 2L }
    );
    when(sessionRepository.countSessionsByClassIdsAndStatuses(List.of(classId))).thenReturn(mockRows);

    Map<UUID, ClassScheduleInfo> result = adapter.getScheduleInfoBatch(inputWithNulls);
    assertEquals(1, result.size());
    assertEquals("SCHEDULED", result.get(classId).scheduleStatus());
    assertEquals(0, result.get(classId).completedCount());
    assertEquals(2, result.get(classId).scheduledCount());
    assertEquals(2, result.get(classId).totalCount());
    assertEquals(0, result.get(classId).sessionCount());
  }
}
