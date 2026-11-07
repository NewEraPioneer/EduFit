package vn.edufit.progress.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.PeriodReport;

/** Cổng kho dữ liệu cho báo cáo kỳ. */
public interface PeriodReportDomainRepository {

  /** Lưu báo cáo; sai phiên bản (sửa đồng thời) ném lỗi xung đột. */
  PeriodReport save(PeriodReport report);

  Optional<PeriodReport> findById(UUID reportId);

  boolean existsByClassIdAndPeriodIndex(UUID classId, int periodIndex);

  /** Báo cáo của lớp, kỳ mới nhất trước. */
  List<PeriodReport> findByClassId(UUID classId);

  /** Bản nháp chờ gửi của các lớp, sắp đến hạn tự gửi trước. */
  List<PeriodReport> findDraftsByClassIdIn(Collection<UUID> classIds);

  /** Bản nháp đã đến hạn tự gửi. */
  List<PeriodReport> findDraftsDueBy(Instant now);
}
