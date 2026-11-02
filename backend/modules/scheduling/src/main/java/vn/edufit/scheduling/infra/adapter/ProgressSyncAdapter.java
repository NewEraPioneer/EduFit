package vn.edufit.scheduling.infra.adapter;

import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edufit.scheduling.application.port.ProgressSyncPort;

/**
 * Outbound Adapter hiện thực hóa {@link ProgressSyncPort} để đồng bộ dữ liệu tiến độ.
 *
 * <p>Theo chuẩn thiết kế Modular Monolith:
 * Class này cô lập logic đồng bộ biên bản buổi học, có thể gọi trực tiếp Facade nội bộ
 * hoặc ghi log tiến độ mà không làm ảnh hưởng đến luồng giao dịch chính của {@code scheduling}.
 */
@Slf4j
@Component
public class ProgressSyncAdapter implements ProgressSyncPort {

  @Override
  public void recordSessionNote(UUID sessionId, UUID classId, String content, Instant recordedAt) {
    log.info(
        "[PROGRESS-SYNC] Đồng bộ biên bản buổi học sang module Progress: sessionId={}, classId={}, noteLength={}, recordedAt={}",
        sessionId,
        classId,
        content != null ? content.length() : 0,
        recordedAt
    );
    // Khi module progress sẵn sàng, ta có thể inject ProgressFacade và ủy thác tại đây
  }
}
