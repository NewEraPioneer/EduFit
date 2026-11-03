package vn.edufit.progress.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.model.SessionMilestoneLink;

/**
 * Cổng kho dữ liệu cho liên kết buổi học ↔ cột mốc. Liên kết ACTUAL được ghi cùng phản hồi buổi học
 * ({@link SessionFeedbackDomainRepository#save}); cổng này quản lý liên kết PLANNED và đọc cả hai loại.
 */
public interface SessionMilestoneLinkDomainRepository {

  List<SessionMilestoneLink> findBySessionIdIn(Collection<UUID> sessionIds);

  /** Thay toàn bộ liên kết PLANNED của buổi học bằng danh sách mới (rỗng = bỏ dự kiến). */
  void replacePlanned(UUID sessionId, Collection<UUID> milestoneIds, UUID createdBy, Instant now);
}
